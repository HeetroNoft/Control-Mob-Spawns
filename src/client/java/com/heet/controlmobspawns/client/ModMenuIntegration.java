package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.client.screen.ConfigScreens;
import com.heet.controlmobspawns.client.screen.HomeScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::openRoot;
	}

	/** From the title screen the global file is edited locally; in a world the server is asked. */
	public static Screen openRoot(Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
			return new AlertScreen(() -> minecraft.gui.setScreen(parent), Component.translatable("controlmobspawns.title"),
				Component.translatable("controlmobspawns.gui.yacl_missing"));
		}
		if (minecraft.level == null) {
			return ConfigScreens.create(parent, ClientSession.local());
		}
		return new HomeScreen(parent);
	}
}
