package com.heet.controlmobspawns.spawn;

import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

/** Implemented on every entity by a mixin: remembers why the entity was created. */
public interface SpawnReasonHolder {
	@Nullable EntitySpawnReason cms$getSpawnReason();

	void cms$setSpawnReason(@Nullable EntitySpawnReason reason);
}
