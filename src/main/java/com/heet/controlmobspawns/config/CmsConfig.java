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
	/** Named groups of mobs sharing settings, highest priority first. A mob's own settings come before them. */
	public List<MobGroup> groups = new ArrayList<>();

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
		/**
		 * On a mob: every setting of the mob applies, vanilla ones included, and its groups are
		 * ignored. When off, only the settings the mob changes win over its groups.
		 */
		public boolean ignoreGroups = false;

		public boolean isDefault() {
			return spawnRate == 100.0 && !banned && !hasDetailedSettings();
		}

		/** Settings other than the spawn rate and the global ban, which are edited in the ⚙ sub-menu. */
		public boolean hasDetailedSettings() {
			return !bannedDimensions.isEmpty() || !bannedBiomes.isEmpty() || !disabledReasons.isEmpty()
				|| !nbtBans.isEmpty() || !nbtProportions.isEmpty() || spawnRule != null || despawn != DespawnMode.DEFAULT
				|| ignoreGroups;
		}

		public MobSettings copy() {
			return ConfigManager.GSON.fromJson(ConfigManager.GSON.toJsonTree(this), MobSettings.class);
		}

		/** Replaces every value with a copy of {@code other}'s, for paste. */
		public void setFrom(MobSettings other) {
			MobSettings copy = other.copy();
			spawnRate = copy.spawnRate;
			banned = copy.banned;
			bannedDimensions = copy.bannedDimensions;
			bannedBiomes = copy.bannedBiomes;
			disabledReasons = copy.disabledReasons;
			nbtBans = copy.nbtBans;
			nbtProportions = copy.nbtProportions;
			spawnRule = copy.spawnRule;
			despawn = copy.despawn;
			ignoreGroups = copy.ignoreGroups;
		}

		/**
		 * Writes what differs from vanilla here into {@code target}, for editing several mobs at
		 * once: changed simple values replace the target's, list entries are added, and the
		 * proportions of a path replace the target's for that path.
		 */
		public void applyChangesTo(MobSettings target) {
			MobSettings copy = copy();
			if (copy.spawnRate != 100.0) {
				target.spawnRate = copy.spawnRate;
			}
			if (copy.banned) {
				target.banned = true;
			}
			if (copy.despawn != null && copy.despawn != DespawnMode.DEFAULT) {
				target.despawn = copy.despawn;
			}
			if (copy.spawnRule != null) {
				target.spawnRule = copy.spawnRule;
			}
			addMissing(copy.bannedDimensions, target.bannedDimensions);
			addMissing(copy.bannedBiomes, target.bannedBiomes);
			addMissing(copy.disabledReasons, target.disabledReasons);
			if (copy.ignoreGroups) {
				target.ignoreGroups = true;
			}
			target.nbtBans.addAll(copy.nbtBans);
			List<String> paths = copy.nbtProportions.stream().map(proportion -> proportion.path).toList();
			target.nbtProportions.removeIf(existing -> paths.contains(existing.path));
			target.nbtProportions.addAll(copy.nbtProportions);
		}

		private static void addMissing(List<String> from, List<String> to) {
			for (String entry : from) {
				if (!to.contains(entry)) {
					to.add(entry);
				}
			}
		}
	}

	public static class MobGroup {
		public String name = "";
		/**
		 * Entity ids, entity tags prefixed with '#' and mob categories prefixed with '@' (for
		 * example "@monster"). An entry prefixed with '!' excludes what it matches: "@monster" and
		 * "!minecraft:zombie" are every monster but zombies.
		 */
		public List<String> members = new ArrayList<>();
		public MobSettings settings = new MobSettings();
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
