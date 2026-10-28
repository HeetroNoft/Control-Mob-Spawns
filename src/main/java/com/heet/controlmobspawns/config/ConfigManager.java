package com.heet.controlmobspawns.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heet.controlmobspawns.ControlMobSpawns;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Loads the global config file and the per-world override, and holds the merged runtime config.
 */
public final class ConfigManager {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = ControlMobSpawns.MOD_ID + ".json";

	private static JsonObject global = new JsonObject();
	private static JsonObject worldOverride = new JsonObject();
	private static @Nullable Path worldPath;
	private static volatile RuntimeConfig active = RuntimeConfig.EMPTY;

	private ConfigManager() {
	}

	public static RuntimeConfig active() {
		return active;
	}

	public static Path globalPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void loadGlobal() {
		boolean exists = Files.exists(globalPath());
		global = toJson(fromJson(read(globalPath())));
		if (!exists) {
			write(globalPath(), global);
		}
		rebuild();
	}

	public static void onServerStarting(MinecraftServer server) {
		worldPath = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME);
		global = toJson(fromJson(read(globalPath())));
		worldOverride = read(worldPath);
		rebuild();
	}

	public static void onServerStopped() {
		worldPath = null;
		worldOverride = new JsonObject();
		rebuild();
	}

	public static CmsConfig globalConfig() {
		return fromJson(global);
	}

	public static CmsConfig effectiveConfig() {
		return fromJson(JsonTree.merge(global, worldOverride));
	}

	public static void saveGlobal(CmsConfig config) {
		config.prune();
		global = toJson(config);
		write(globalPath(), global);
		rebuild();
	}

	/** Saves the world override as the difference between {@code effective} and the global config. */
	public static void saveWorld(CmsConfig effective) {
		if (worldPath == null) {
			return;
		}
		effective.prune();
		worldOverride = JsonTree.diff(global, toJson(effective));
		write(worldPath, worldOverride);
		rebuild();
	}

	private static void rebuild() {
		active = new RuntimeConfig(effectiveConfig());
	}

	public static JsonObject toJson(CmsConfig config) {
		return GSON.toJsonTree(config).getAsJsonObject();
	}

	public static CmsConfig fromJson(JsonObject json) {
		CmsConfig config = GSON.fromJson(json, CmsConfig.class);
		return config == null ? new CmsConfig() : config;
	}

	public static CmsConfig fromJsonString(String json) {
		return fromJson(JsonParser.parseString(json).getAsJsonObject());
	}

	public static String toJsonString(CmsConfig config) {
		return GSON.toJson(config);
	}

	public static CmsConfig copy(CmsConfig config) {
		return fromJson(toJson(config));
	}

	private static JsonObject read(Path path) {
		if (!Files.exists(path)) {
			return new JsonObject();
		}
		try {
			return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
		} catch (Exception e) {
			ControlMobSpawns.LOGGER.error("Could not read {}, using defaults", path, e);
			return new JsonObject();
		}
	}

	private static void write(Path path, JsonObject json) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException e) {
			ControlMobSpawns.LOGGER.error("Could not write {}", path, e);
		}
	}
}
