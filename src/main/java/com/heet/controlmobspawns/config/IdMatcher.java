package com.heet.controlmobspawns.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** Matches registry entries against a list of ids and '#tags'. */
public final class IdMatcher<T> {
	private final Set<Identifier> ids = new HashSet<>();
	private final List<TagKey<T>> tags = new ArrayList<>();

	public IdMatcher(ResourceKey<? extends Registry<T>> registry, List<String> entries) {
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				Identifier id = Identifier.tryParse(entry.substring(1));
				if (id != null) {
					tags.add(TagKey.create(registry, id));
				}
			} else {
				Identifier id = Identifier.tryParse(entry);
				if (id != null) {
					ids.add(id);
				}
			}
		}
	}

	public boolean isEmpty() {
		return ids.isEmpty() && tags.isEmpty();
	}

	public boolean matches(Holder<T> holder) {
		if (!ids.isEmpty() && holder.unwrapKey().map(key -> ids.contains(key.identifier())).orElse(false)) {
			return true;
		}
		for (TagKey<T> tag : tags) {
			if (holder.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
