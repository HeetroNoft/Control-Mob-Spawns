package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.ControlMobSpawns;
import com.heet.controlmobspawns.client.screen.MainConfigScreen;
import com.heet.controlmobspawns.network.CmsPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** An unbound key that opens this world's configuration (the server's, in multiplayer) while playing. */
public final class WorldConfigKey {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ControlMobSpawns.MOD_ID, "main"));
	public static final KeyMapping OPEN_WORLD_CONFIG = new KeyMapping(
		"key.controlmobspawns.open_world_config", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

	private WorldConfigKey() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(OPEN_WORLD_CONFIG);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (OPEN_WORLD_CONFIG.consumeClick()) {
				open(minecraft);
			}
		});
	}

	/** Asks the server for the configuration and opens the world scope once it answers. */
	public static void open(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(CmsPayloads.RequestSync.TYPE)) {
			minecraft.player.sendOverlayMessage(Component.translatable("controlmobspawns.gui.home.missing_on_server"));
			return;
		}
		ControlMobSpawnsClient.setSyncListener(sync -> {
			ControlMobSpawnsClient.setSyncListener(null);
			if (minecraft.player == null || minecraft.gui.screen() != null) {
				return;
			}
			if (sync.canEdit()) {
				minecraft.gui.setScreen(new MainConfigScreen(null, ClientSession.remote(ClientSession.Scope.WORLD, sync)));
			} else {
				minecraft.player.sendOverlayMessage(Component.translatable("controlmobspawns.message.no_permission"));
			}
		});
		ClientPlayNetworking.send(new CmsPayloads.RequestSync());
	}
}
