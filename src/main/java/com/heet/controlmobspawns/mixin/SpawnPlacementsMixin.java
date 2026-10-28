package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnControl;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
	/** A configured spawn rule replaces the mob's own spawn conditions (peaceful still applies). */
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static <T extends Entity> void cms$customSpawnRule(
		EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir
	) {
		if (!type.isAllowedInPeaceful() && level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		Boolean result = SpawnControl.checkSpawnRule(type, level, pos);
		if (result != null) {
			cir.setReturnValue(result);
		}
	}
}
