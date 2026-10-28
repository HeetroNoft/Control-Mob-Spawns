package com.heet.controlmobspawns.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serialized configuration. Missing or null values mean "keep vanilla behaviour".
 */
public class CmsConfig {
	public int permissionLevel = 2;
	public Coverage coverage = new Coverage();
	public Despawn despawn = new Despawn();
	/** Mob category name (e.g. "monster") to its max instances per chunk. */
	public Map<String, Integer> mobCaps = new LinkedHashMap<>();
	/** Entity type id to its settings. */
	public Map<String, MobSettings> mobs = new TreeMap<>();
	public List<BiomeSpawn> biomeSpawns = new ArrayList<>();

	public static class Coverage {
		public boolean moddedMobs = true;
		public boolean eggsAndCommands = false;
		public boolean removeExisting = false;
	}

	public static class Despawn {
		/** Mob category name to its despawn distances. */
		public Map<String, DespawnDistance> distances = new LinkedHashMap<>();
		public boolean itemPickersDespawn = false;
		public boolean forceIncludesNamed = false;
		/** "Despawn" in the Current mobs tab acts on the player's dimension instead of all of them. */
		public boolean despawnButtonCurrentDimension = false;
	}

	public static class DespawnDistance {
		public Integer instant;
		public Integer random;
	}

	public enum DespawnMode {
		DEFAULT, NEVER, FORCE
	}

	public static class MobSettings {
		public double spawnRate = 100.0;
		public boolean banned = false;
		public List<String> bannedDimensions = new ArrayList<>();
		/** Biome ids or biome tags prefixed with '#'. */
		public List<String> bannedBiomes = new ArrayList<>();
		public List<String> disabledReasons = new ArrayList<>();
		public List<NbtBan> nbtBans = new ArrayList<>();
		public List<NbtProportion> nbtProportions = new ArrayList<>();
		public SpawnRule spawnRule;
		public DespawnMode despawn = DespawnMode.DEFAULT;

		public boolean isDefault() {
			return spawnRate == 100.0 && !banned && !hasDetailedSettings();
		}

		/** Settings other than the spawn rate and the global ban, which are edited in the ⚙ sub-menu. */
		public boolean hasDetailedSettings() {
			return !bannedDimensions.isEmpty() || !bannedBiomes.isEmpty() || !disabledReasons.isEmpty()
				|| !nbtBans.isEmpty() || !nbtProportions.isEmpty() || spawnRule != null || despawn != DespawnMode.DEFAULT;
		}
	}

	public static class NbtBan {
		public String path = "";
		public String value = "";
	}

	public static class NbtProportion {
		public String path = "";
		public String value = "";
		public double percent = 50.0;
	}

	public enum TimeOfDay {
		ANY, DAY, NIGHT
	}

	public enum Weather {
		ANY, CLEAR, RAIN, THUNDER
	}

	public static class SpawnRule {
		public boolean enabled = true;
		public Integer minBlockLight;
		public Integer maxBlockLight;
		public Integer minSkyLight;
		public Integer maxSkyLight;
		public Integer minY;
		public Integer maxY;
		/** Block ids or block tags prefixed with '#'. Empty means any block. */
		public List<String> blocksBelow = new ArrayList<>();
		public TimeOfDay time = TimeOfDay.ANY;
		public Weather weather = Weather.ANY;
		/** Moon phase indexes (0 = full moon). Empty means any phase. */
		public List<Integer> moonPhases = new ArrayList<>();
	}

	public static class BiomeSpawn {
		public String entity = "";
		/** Biome ids or biome tags prefixed with '#'. */
		public List<String> biomes = new ArrayList<>();
		public int weight = 10;
		public int minCount = 1;
		public int maxCount = 4;
	}

	public MobSettings getOrCreateMob(String id) {
		return mobs.computeIfAbsent(id, k -> new MobSettings());
	}

	/** Drops mob entries that only hold default values. */
	public void prune() {
		mobs.values().removeIf(MobSettings::isDefault);
	}
}
