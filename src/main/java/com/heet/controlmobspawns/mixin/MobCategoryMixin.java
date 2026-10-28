package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.config.ConfigManager;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {
	@Inject(method = "getMaxInstancesPerChunk", at = @At("HEAD"), cancellable = true)
	private void cms$mobCap(CallbackInfoReturnable<Integer> cir) {
		Integer cap = ConfigManager.active().caps.get((MobCategory) (Object) this);
		if (cap != null) {
			cir.setReturnValue(cap);
		}
	}

	@Inject(method = "getDespawnDistance", at = @At("HEAD"), cancellable = true)
	private void cms$instantDespawnDistance(CallbackInfoReturnable<Integer> cir) {
		Integer distance = ConfigManager.active().instantDespawn.get((MobCategory) (Object) this);
		if (distance != null) {
			cir.setReturnValue(distance);
		}
	}

	@Inject(method = "getNoDespawnDistance", at = @At("HEAD"), cancellable = true)
	private void cms$randomDespawnDistance(CallbackInfoReturnable<Integer> cir) {
		Integer distance = ConfigManager.active().randomDespawn.get((MobCategory) (Object) this);
		if (distance != null) {
			cir.setReturnValue(distance);
		}
	}
}
