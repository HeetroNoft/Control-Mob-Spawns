package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * NBT paths received from the server, per entity type, for the NBT editors' suggestions. A group
 * edits several mobs at once: its suggestions are the union of its members' paths.
 */
public final class NbtSuggestions {
	private static final Map<String, List<CmsPayloads.NbtPathInfo>> CACHE = new HashMap<>();
	private static final Set<String> REQUESTED = new HashSet<>();
	private static @Nullable Runnable listener;

	private NbtSuggestions() {
	}

	/** False from the title screen or on a server without the mod: suggestions need a world. */
	public static boolean available() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(CmsPayloads.RequestNbtPaths.TYPE);
	}

	/** Asks the server once per connection; existing mobs may add values, so the list is not refreshed afterwards. */
	public static void request(String entity) {
		if (available() && REQUESTED.add(entity)) {
			ClientPlayNetworking.send(new CmsPayloads.RequestNbtPaths(entity));
		}
	}

	public static void request(List<String> entities) {
		entities.forEach(NbtSuggestions::request);
	}

	/** A path of several mobs: the values seen on any of them and the mobs that have it. */
	public record SharedPath(String path, List<String> values, List<String> entities) {
		public int mobs() {
			return entities.size();
		}
	}

	/** How many of these mobs have their paths received. */
	public static int loaded(List<String> entities) {
		return (int) entities.stream().filter(CACHE::containsKey).count();
	}

	/**
	 * The paths of every received mob among these, with their values merged: first the paths all
	 * of them have, then those only some have, each part sorted.
	 */
	public static List<SharedPath> shared(List<String> entities) {
		Map<String, Set<String>> values = new TreeMap<>();
		Map<String, List<String>> owners = new HashMap<>();
		for (String entity : entities) {
			List<CmsPayloads.NbtPathInfo> paths = CACHE.get(entity);
			if (paths == null) {
				continue;
			}
			for (CmsPayloads.NbtPathInfo info : paths) {
				values.computeIfAbsent(info.path(), key -> new LinkedHashSet<>()).addAll(info.values());
				owners.computeIfAbsent(info.path(), key -> new ArrayList<>()).add(entity);
			}
		}
		List<SharedPath> result = new ArrayList<>();
		values.forEach((path, seen) -> result.add(new SharedPath(path, List.copyOf(seen), List.copyOf(owners.get(path)))));
		int loaded = loaded(entities);
		result.sort(Comparator.comparing(info -> info.mobs() < loaded));
		return result;
	}

	public static List<String> paths(List<String> entities) {
		return shared(entities).stream().map(SharedPath::path).toList();
	}

	public static List<String> values(List<String> entities, String path) {
		return shared(entities).stream().filter(info -> info.path().equals(path)).findFirst().map(SharedPath::values).orElse(List.of());
	}

	static void accept(CmsPayloads.NbtPaths payload) {
		CACHE.put(payload.entity(), payload.paths());
		if (listener != null) {
			listener.run();
		}
	}

	public static void setListener(@Nullable Runnable newListener) {
		listener = newListener;
	}

	/** Called on disconnect: another server has other mobs and data. */
	static void clear() {
		CACHE.clear();
		REQUESTED.clear();
	}
}
