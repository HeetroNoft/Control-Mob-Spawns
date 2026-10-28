package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void cms$blockBannedEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (SpawnControl.shouldBlockFresh(entity, level.dimension(), level.getBiome(entity.blockPosition()), level.getRandom())) {
			cir.setReturnValue(false);
		}
	}
}
