package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.client.screen.HomeScreen;
import com.heet.controlmobspawns.client.screen.MainConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::openRoot;
	}

	/** From the title screen the global file is edited locally; in a world the server is asked. */
	public static Screen openRoot(Screen parent) {
		if (Minecraft.getInstance().level == null) {
			return new MainConfigScreen(parent, ClientSession.local());
		}
		return new HomeScreen(parent);
	}
}
