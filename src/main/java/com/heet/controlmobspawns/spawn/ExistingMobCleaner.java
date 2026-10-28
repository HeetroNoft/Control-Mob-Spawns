package com.heet.controlmobspawns.spawn;

import com.heet.controlmobspawns.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Removes banned mobs that are already in the world, when the "existing mobs" toggle is on. */
public final class ExistingMobCleaner {
	private static final List<Entity> PENDING = new ArrayList<>();

	private ExistingMobCleaner() {
	}

	/** Entities are queued and removed at the end of the tick, outside of chunk loading. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (ConfigManager.active().raw.coverage.removeExisting
			&& SpawnControl.isBannedExisting(entity, level.dimension(), level.getBiome(entity.blockPosition()))) {
			PENDING.add(entity);
		}
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Entity entity : PENDING) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
		PENDING.clear();
	}

	public static void sweep(MinecraftServer server) {
		if (!ConfigManager.active().raw.coverage.removeExisting) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				onEntityLoad(entity, level);
			}
		}
	}
}
