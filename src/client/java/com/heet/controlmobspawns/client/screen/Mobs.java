package com.heet.controlmobspawns.client.screen;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;

/** Entity type helpers for the screens. */
public final class Mobs {
	private static @Nullable List<EntityType<?>> cache;

	private Mobs() {
	}

	/** Every mob: types with a spawn category, or with a spawn egg (golems, bosses...). */
	public static List<EntityType<?>> all() {
		if (cache == null) {
			cache = BuiltInRegistries.ENTITY_TYPE.stream()
				.filter(type -> type.getCategory() != MobCategory.MISC || namedSpawnEgg(type).isPresent())
				.sorted(Comparator.comparing(type -> type.getDescription().getString()))
				.toList();
		}
		return cache;
	}

	public static String id(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
	}

	public static Component name(EntityType<?> type) {
		return type.getDescription();
	}

	/** The mob's name, in red when it is banned. */
	public static Component label(EntityType<?> type, boolean banned) {
		return banned ? name(type).copy().withStyle(ChatFormatting.RED) : name(type);
	}

	/**
	 * Item components are only bound once a world is loaded: from the title screen, spawn eggs
	 * cannot be looked up by entity type and items cannot be rendered.
	 */
	private static boolean componentsBound() {
		return Items.STONE.builtInRegistryHolder().areComponentsBound();
	}

	/** Spawn eggs follow the "<mob>_spawn_egg" naming convention, which needs no components. */
	private static Optional<Item> namedSpawnEgg(EntityType<?> type) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return BuiltInRegistries.ITEM.getOptional(id.withSuffix("_spawn_egg")).filter(item -> item instanceof SpawnEggItem);
	}

	public static ItemStack icon(EntityType<?> type) {
		if (!componentsBound()) {
			return ItemStack.EMPTY;
		}
		return SpawnEggItem.byId(type).map(ItemStack::new)
			.or(() -> namedSpawnEgg(type).map(ItemStack::new))
			.orElse(ItemStack.EMPTY);
	}

	public static boolean matches(EntityType<?> type, String search) {
		if (search.isBlank()) {
			return true;
		}
		String query = search.toLowerCase(Locale.ROOT).trim();
		return id(type).contains(query) || name(type).getString().toLowerCase(Locale.ROOT).contains(query);
	}

	public static boolean contains(String value, String search) {
		return search.isBlank() || value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT).trim());
	}
}
