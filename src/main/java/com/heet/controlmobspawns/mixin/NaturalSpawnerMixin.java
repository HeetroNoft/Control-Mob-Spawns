package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnControl;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@ModifyReturnValue(method = "mobsAt", at = @At("RETURN"))
	private static WeightedList<MobSpawnSettings.SpawnerData> cms$adjustNaturalSpawns(
		WeightedList<MobSpawnSettings.SpawnerData> original, ServerLevel level, StructureManager structureManager,
		ChunkGenerator generator, MobCategory category, BlockPos pos, @Nullable Holder<Biome> biome
	) {
		return SpawnControl.adjustSpawnList(original, level.dimension(), biome != null ? biome : level.getBiome(pos), category, EntitySpawnReason.NATURAL);
	}

	@ModifyExpressionValue(
		method = "spawnMobsForChunkGeneration",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobs(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;")
	)
	private static WeightedList<MobSpawnSettings.SpawnerData> cms$adjustChunkGenerationSpawns(
		WeightedList<MobSpawnSettings.SpawnerData> original, ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random
	) {
		return SpawnControl.adjustSpawnList(original, level.getLevel().dimension(), biome, MobCategory.CREATURE, EntitySpawnReason.CHUNK_GENERATION);
	}
}
