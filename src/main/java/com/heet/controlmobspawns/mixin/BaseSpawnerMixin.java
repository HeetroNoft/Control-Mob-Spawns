package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnControl;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {
	/** Same as natural spawning: a configured spawn rule replaces the mob's own check. */
	@WrapOperation(
		method = "serverTick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z")
	)
	private boolean cms$skipMobSpawnCheck(Mob mob, LevelAccessor level, EntitySpawnReason reason, Operation<Boolean> original) {
		return SpawnControl.hasSpawnRule(mob.getType()) || original.call(mob, level, reason);
	}
}
