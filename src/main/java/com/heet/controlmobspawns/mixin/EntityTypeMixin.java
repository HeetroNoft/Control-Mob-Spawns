package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnReasonHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeMixin<T extends Entity> {
	@Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void cms$rememberSpawnReason(Level level, EntitySpawnRequest request, CallbackInfoReturnable<T> cir) {
		if (cir.getReturnValue() instanceof SpawnReasonHolder holder) {
			holder.cms$setSpawnReason(request.reason());
		}
	}
}
