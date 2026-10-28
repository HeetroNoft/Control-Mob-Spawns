package com.heet.controlmobspawns.mixin;

import com.heet.controlmobspawns.spawn.SpawnReasonHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin implements SpawnReasonHolder {
	@Unique
	private @Nullable EntitySpawnReason cms$spawnReason;

	@Override
	public @Nullable EntitySpawnReason cms$getSpawnReason() {
		return cms$spawnReason;
	}

	@Override
	public void cms$setSpawnReason(@Nullable EntitySpawnReason reason) {
		cms$spawnReason = reason;
	}
}
