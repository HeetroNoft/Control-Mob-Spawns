package com.heet.controlmobspawns.spawn;

import com.heet.controlmobspawns.ControlMobSpawns;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** The spawn logic called from the mixins. */
public final class SpawnControl {
	/** Reasons where the entity is not new: these are never blocked. */
	private static final Set<EntitySpawnReason> NOT_FRESH = EnumSet.of(EntitySpawnReason.LOAD, EntitySpawnReason.DIMENSION_TRAVEL);
	/** Reasons covered by the "spawn eggs and /summon" toggle. */
	private static final Set<EntitySpawnReason> PLAYER_FORCED = EnumSet.of(
		EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.COMMAND, EntitySpawnReason.DISPENSER);
	/**
	 * Reasons where NBT proportions are not forced: commands carry explicit NBT, and the others
	 * copy their data from an existing entity.
	 */
	private static final Set<EntitySpawnReason> KEEP_NBT = EnumSet.of(
		EntitySpawnReason.COMMAND, EntitySpawnReason.CONVERSION, EntitySpawnReason.BUCKET, EntitySpawnReason.BREEDING);
	private static final long MAX_WEIGHT = 1_000_000_000L;

	private SpawnControl() {
	}

	/**
	 * Applies spawn rates, location bans, disabled spawn reasons and biome spawns to the list
	 * of mobs that can spawn at a position.
	 */
	public static WeightedList<MobSpawnSettings.SpawnerData> adjustSpawnList(
		WeightedList<MobSpawnSettings.SpawnerData> list, ResourceKey<Level> dimension, Holder<Biome> biome,
		MobCategory category, EntitySpawnReason reason
	) {
		RuntimeConfig config = ConfigManager.active();
		if (config.mobs.isEmpty() && config.biomeSpawns.isEmpty()) {
			return list;
		}
		WeightedList.Builder<MobSpawnSettings.SpawnerData> builder = WeightedList.builder();
		boolean changed = false;
		for (Weighted<MobSpawnSettings.SpawnerData> entry : list.unwrap()) {
			RuntimeConfig.CompiledMob mob = config.mob(entry.value().type());
			if (mob != null && (mob.disabledReasons.contains(reason) || isBannedAt(mob, dimension, biome))) {
				changed = true;
				continue;
			}
			if (mob != null && mob.spawnRate != 100.0) {
				changed = true;
			}
			builder.add(entry.value(), scaledWeight(entry.weight(), mob));
		}
		for (RuntimeConfig.CompiledBiomeSpawn spawn : config.biomeSpawns) {
			if (spawn.type().getCategory() != category || !spawn.biomes().matches(biome)) {
				continue;
			}
			MobSpawnSettings.SpawnerData data = new MobSpawnSettings.SpawnerData(spawn.type(), spawn.minCount(), spawn.maxCount());
			if (list.contains(data)) {
				continue;
			}
			RuntimeConfig.CompiledMob mob = config.mob(spawn.type());
			if (mob != null && (mob.disabledReasons.contains(reason) || isBannedAt(mob, dimension, biome))) {
				continue;
			}
			builder.add(data, scaledWeight(spawn.weight(), mob));
			changed = true;
		}
		return changed ? builder.build() : list;
	}

	/** Weights are multiplied by 100 so that percentages below 100 keep their precision. */
	private static int scaledWeight(int weight, RuntimeConfig.CompiledMob mob) {
		double rate = mob == null ? 100.0 : mob.spawnRate;
		return (int) Math.clamp(Math.round(weight * rate), 1L, MAX_WEIGHT);
	}

	private static boolean isBannedAt(RuntimeConfig.CompiledMob mob, ResourceKey<Level> dimension, Holder<Biome> biome) {
		return mob.banned || mob.bannedDimensions.contains(dimension.identifier()) || mob.bannedBiomes.matches(biome);
	}

	/**
	 * Called just before a new entity is added to the world. Forces NBT proportions, then
	 * returns true when the entity must not be added.
	 */
	public static boolean shouldBlockFresh(Entity entity, ResourceKey<Level> dimension, Holder<Biome> biome, RandomSource random) {
		if (entity instanceof Player) {
			return false;
		}
		EntitySpawnReason reason = ((SpawnReasonHolder) entity).cms$getSpawnReason();
		if (reason == null || NOT_FRESH.contains(reason)) {
			return false;
		}
		RuntimeConfig config = ConfigManager.active();
		RuntimeConfig.CompiledMob mob = config.mob(entity.getType());
		if (mob == null) {
			return false;
		}
		if (mob.disabledReasons.contains(reason)) {
			return true;
		}
		if (PLAYER_FORCED.contains(reason) && !config.raw.coverage.eggsAndCommands) {
			return false;
		}
		if (!KEEP_NBT.contains(reason)) {
			applyProportions(entity, mob, random);
		}
		return isBannedAt(mob, dimension, biome) || matchesNbtBan(entity, mob);
	}

	/** Used for entities already in the world: location and NBT bans only. */
	public static boolean isBannedExisting(Entity entity, ResourceKey<Level> dimension, Holder<Biome> biome) {
		if (entity instanceof Player) {
			return false;
		}
		RuntimeConfig.CompiledMob mob = ConfigManager.active().mob(entity.getType());
		return mob != null && (isBannedAt(mob, dimension, biome) || matchesNbtBan(entity, mob));
	}

	private static boolean matchesNbtBan(Entity entity, RuntimeConfig.CompiledMob mob) {
		if (mob.nbtBans.isEmpty()) {
			return false;
		}
		CompoundTag tag = save(entity);
		for (RuntimeConfig.NbtCheck check : mob.nbtBans) {
			try {
				for (Tag actual : check.path().get(tag)) {
					if (NbtUtils.compareNbt(check.value(), actual, true)) {
						return true;
					}
				}
			} catch (CommandSyntaxException ignored) {
				// The path does not exist on this entity.
			}
		}
		return false;
	}

	/**
	 * Picks a value for each NBT path according to its percentages. When a single boolean value is
	 * configured, the remaining percentage gets the opposite value; otherwise it keeps the vanilla value.
	 */
	private static void applyProportions(Entity entity, RuntimeConfig.CompiledMob mob, RandomSource random) {
		if (mob.proportions.isEmpty()) {
			return;
		}
		CompoundTag tag = save(entity);
		boolean changed = false;
		for (RuntimeConfig.ProportionGroup group : mob.proportions) {
			Tag chosen = pick(group.entries(), random.nextDouble() * 100.0);
			if (chosen == null && group.entries().size() == 1 && isBoolean(group.entries().getFirst().value())) {
				chosen = ByteTag.valueOf(((ByteTag) group.entries().getFirst().value()).value() == 0);
			}
			if (chosen != null && set(group.path(), tag, chosen)) {
				changed = true;
			}
		}
		if (changed) {
			load(entity, tag);
		}
	}

	private static Tag pick(List<RuntimeConfig.ProportionEntry> entries, double roll) {
		double cumulative = 0;
		for (RuntimeConfig.ProportionEntry entry : entries) {
			cumulative += entry.percent();
			if (roll < cumulative) {
				return entry.value();
			}
		}
		return null;
	}

	private static boolean isBoolean(Tag tag) {
		return tag instanceof ByteTag byteTag && (byteTag.value() == 0 || byteTag.value() == 1);
	}

	private static boolean set(NbtPathArgument.NbtPath path, CompoundTag tag, Tag value) {
		try {
			return path.set(tag, value) > 0;
		} catch (CommandSyntaxException e) {
			return false;
		}
	}

	private static CompoundTag save(Entity entity) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), ControlMobSpawns.LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
			entity.saveWithoutId(output);
			return output.buildResult();
		}
	}

	private static void load(Entity entity, CompoundTag tag) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), ControlMobSpawns.LOGGER)) {
			var uuid = entity.getUUID();
			entity.load(TagValueInput.create(reporter, entity.registryAccess(), tag));
			entity.setUUID(uuid);
		}
	}

	/** Returns the result of the configured spawn rule, or null to keep the vanilla check. */
	public static Boolean checkSpawnRule(EntityType<?> type, ServerLevelAccessor level, BlockPos pos) {
		RuntimeConfig.CompiledMob mob = ConfigManager.active().mob(type);
		if (mob == null || mob.spawnRule == null) {
			return null;
		}
		CmsConfig.SpawnRule rule = mob.spawnRule.rule;
		if (!inRange(level.getBrightness(LightLayer.BLOCK, pos), rule.minBlockLight, rule.maxBlockLight)
			|| !inRange(level.getBrightness(LightLayer.SKY, pos), rule.minSkyLight, rule.maxSkyLight)
			|| !inRange(pos.getY(), rule.minY, rule.maxY)) {
			return false;
		}
		if (!mob.spawnRule.blocksBelow.isEmpty() && !mob.spawnRule.blocksBelow.matches(level.getBlockState(pos.below()).typeHolder())) {
			return false;
		}
		Level world = level.getLevel();
		switch (rule.time) {
			case DAY -> {
				if (!world.isBrightOutside()) {
					return false;
				}
			}
			case NIGHT -> {
				if (world.isBrightOutside()) {
					return false;
				}
			}
			case ANY -> {
			}
		}
		switch (rule.weather) {
			case CLEAR -> {
				if (world.isRaining()) {
					return false;
				}
			}
			case RAIN -> {
				if (!world.isRaining()) {
					return false;
				}
			}
			case THUNDER -> {
				if (!world.isThundering()) {
					return false;
				}
			}
			case ANY -> {
			}
		}
		if (!rule.moonPhases.isEmpty()) {
			MoonPhase phase = world.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, Vec3.atCenterOf(pos));
			return rule.moonPhases.contains(phase.index());
		}
		return true;
	}

	private static boolean inRange(int value, Integer min, Integer max) {
		return (min == null || value >= min) && (max == null || value <= max);
	}
}
