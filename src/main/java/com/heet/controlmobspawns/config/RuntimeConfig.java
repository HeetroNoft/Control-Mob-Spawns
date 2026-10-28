package com.heet.controlmobspawns.config;

import com.heet.controlmobspawns.ControlMobSpawns;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** The active configuration, pre-parsed for fast lookups during spawning. */
public final class RuntimeConfig {
	public static final RuntimeConfig EMPTY = new RuntimeConfig(new CmsConfig());

	public final CmsConfig raw;
	public final Map<EntityType<?>, CompiledMob> mobs = new IdentityHashMap<>();
	public final Map<MobCategory, Integer> caps = new EnumMap<>(MobCategory.class);
	public final Map<MobCategory, Integer> instantDespawn = new EnumMap<>(MobCategory.class);
	public final Map<MobCategory, Integer> randomDespawn = new EnumMap<>(MobCategory.class);
	public final List<CompiledBiomeSpawn> biomeSpawns = new ArrayList<>();

	public RuntimeConfig(CmsConfig raw) {
		this.raw = raw;
		for (MobCategory category : MobCategory.values()) {
			String name = category.getName();
			Integer cap = raw.mobCaps.get(name);
			if (cap != null && cap >= 0) {
				caps.put(category, cap);
			}
			CmsConfig.DespawnDistance distance = raw.despawn.distances.get(name);
			if (distance != null) {
				if (distance.instant != null && distance.instant >= 0) {
					instantDespawn.put(category, distance.instant);
				}
				if (distance.random != null && distance.random >= 0) {
					randomDespawn.put(category, distance.random);
				}
			}
		}
		raw.mobs.forEach((id, settings) -> {
			EntityType<?> type = entityType(id);
			if (type != null) {
				mobs.put(type, new CompiledMob(id, settings));
			}
		});
		for (CmsConfig.BiomeSpawn spawn : raw.biomeSpawns) {
			EntityType<?> type = entityType(spawn.entity);
			if (type != null && type.getCategory() != MobCategory.MISC && spawn.weight > 0) {
				int min = Math.max(1, spawn.minCount);
				biomeSpawns.add(new CompiledBiomeSpawn(type, new IdMatcher<>(Registries.BIOME, spawn.biomes),
					spawn.weight, min, Math.max(min, spawn.maxCount)));
			}
		}
	}

	public boolean coversType(EntityType<?> type) {
		return raw.coverage.moddedMobs || BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(Identifier.DEFAULT_NAMESPACE);
	}

	/** Settings for this entity type, or null when it is not configured or not covered. */
	public @Nullable CompiledMob mob(EntityType<?> type) {
		CompiledMob mob = mobs.get(type);
		return mob != null && coversType(type) ? mob : null;
	}

	public static @Nullable EntityType<?> entityType(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
	}

	public static Tag parseNbtValue(String value) throws CommandSyntaxException {
		return TagParser.create(NbtOps.INSTANCE).parseFully(value);
	}

	public record CompiledBiomeSpawn(EntityType<?> type, IdMatcher<Biome> biomes, int weight, int minCount, int maxCount) {
	}

	public record NbtCheck(NbtPathArgument.NbtPath path, Tag value) {
	}

	public record ProportionEntry(Tag value, double percent) {
	}

	public record ProportionGroup(NbtPathArgument.NbtPath path, List<ProportionEntry> entries) {
	}

	public static final class CompiledMob {
		public final double spawnRate;
		public final boolean banned;
		public final Set<Identifier> bannedDimensions = new HashSet<>();
		public final IdMatcher<Biome> bannedBiomes;
		public final EnumSet<EntitySpawnReason> disabledReasons = EnumSet.noneOf(EntitySpawnReason.class);
		public final List<NbtCheck> nbtBans = new ArrayList<>();
		public final List<ProportionGroup> proportions = new ArrayList<>();
		public final @Nullable CompiledRule spawnRule;
		public final CmsConfig.DespawnMode despawn;

		CompiledMob(String id, CmsConfig.MobSettings settings) {
			this.spawnRate = settings.spawnRate > 0 ? settings.spawnRate : 100.0;
			this.banned = settings.banned;
			for (String dimension : settings.bannedDimensions) {
				Identifier dimensionId = Identifier.tryParse(dimension);
				if (dimensionId != null) {
					bannedDimensions.add(dimensionId);
				}
			}
			this.bannedBiomes = new IdMatcher<>(Registries.BIOME, settings.bannedBiomes);
			for (String reason : settings.disabledReasons) {
				try {
					disabledReasons.add(EntitySpawnReason.valueOf(reason.toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					ControlMobSpawns.LOGGER.warn("Unknown spawn reason {} for {}", reason, id);
				}
			}
			for (CmsConfig.NbtBan ban : settings.nbtBans) {
				try {
					nbtBans.add(new NbtCheck(NbtPathArgument.NbtPath.of(ban.path), parseNbtValue(ban.value)));
				} catch (CommandSyntaxException e) {
					ControlMobSpawns.LOGGER.warn("Invalid NBT ban {} = {} for {}: {}", ban.path, ban.value, id, e.getMessage());
				}
			}
			Map<String, NbtPathArgument.NbtPath> paths = new LinkedHashMap<>();
			Map<String, List<ProportionEntry>> grouped = new LinkedHashMap<>();
			for (CmsConfig.NbtProportion proportion : settings.nbtProportions) {
				try {
					NbtPathArgument.NbtPath path = NbtPathArgument.NbtPath.of(proportion.path);
					Tag value = parseNbtValue(proportion.value);
					paths.putIfAbsent(proportion.path, path);
					grouped.computeIfAbsent(proportion.path, k -> new ArrayList<>()).add(new ProportionEntry(value, Math.max(0, proportion.percent)));
				} catch (CommandSyntaxException e) {
					ControlMobSpawns.LOGGER.warn("Invalid NBT proportion {} = {} for {}: {}", proportion.path, proportion.value, id, e.getMessage());
				}
			}
			grouped.forEach((path, entries) -> proportions.add(new ProportionGroup(paths.get(path), entries)));
			this.spawnRule = settings.spawnRule != null && settings.spawnRule.enabled ? new CompiledRule(settings.spawnRule) : null;
			this.despawn = settings.despawn == null ? CmsConfig.DespawnMode.DEFAULT : settings.despawn;
		}
	}

	public static final class CompiledRule {
		public final CmsConfig.SpawnRule rule;
		public final IdMatcher<Block> blocksBelow;

		CompiledRule(CmsConfig.SpawnRule rule) {
			this.rule = rule;
			this.blocksBelow = new IdMatcher<>(Registries.BLOCK, rule.blocksBelow);
		}
	}
}
