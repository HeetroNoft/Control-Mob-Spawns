package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnControl;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk generation and structure spawns go through the world generation region, not the level. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void cms$blockBannedEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		WorldGenRegion region = (WorldGenRegion) (Object) this;
		if (SpawnControl.shouldBlockFresh(entity, region.getLevel().dimension(), region.getBiome(entity.blockPosition()), region.getRandom())) {
			cir.setReturnValue(false);
		}
	}
}
