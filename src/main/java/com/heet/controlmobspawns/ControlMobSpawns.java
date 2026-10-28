package com.heet.controlmobspawns;

import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.network.CmsPayloads;
import com.heet.controlmobspawns.network.ServerHandlers;
import com.heet.controlmobspawns.spawn.ExistingMobCleaner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControlMobSpawns implements ModInitializer {
	public static final String MOD_ID = "controlmobspawns";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.loadGlobal();
		CmsPayloads.register();
		ServerHandlers.register();
		ServerLifecycleEvents.SERVER_STARTING.register(ConfigManager::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ConfigManager.onServerStopped());
		ServerEntityEvents.ENTITY_LOAD.register(ExistingMobCleaner::onEntityLoad);
		ServerTickEvents.END_SERVER_TICK.register(ExistingMobCleaner::tick);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
