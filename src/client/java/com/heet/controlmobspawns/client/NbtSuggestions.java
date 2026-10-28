package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/** NBT paths received from the server, per entity type, for the NBT editors' suggestions. */
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

	public static @Nullable List<CmsPayloads.NbtPathInfo> get(String entity) {
		return CACHE.get(entity);
	}

	public static List<String> paths(String entity) {
		List<CmsPayloads.NbtPathInfo> paths = CACHE.get(entity);
		return paths == null ? List.of() : paths.stream().map(CmsPayloads.NbtPathInfo::path).toList();
	}

	public static List<String> values(String entity, String path) {
		List<CmsPayloads.NbtPathInfo> paths = CACHE.get(entity);
		if (paths == null) {
			return List.of();
		}
		return paths.stream().filter(info -> info.path().equals(path)).findFirst().map(CmsPayloads.NbtPathInfo::values).orElse(List.of());
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
