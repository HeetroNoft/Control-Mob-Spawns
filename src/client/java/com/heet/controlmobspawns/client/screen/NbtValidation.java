package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class NbtValidation {
	private NbtValidation() {
	}

	static boolean isPath(String text) {
		try {
			NbtPathArgument.NbtPath.of(text);
			return true;
		} catch (CommandSyntaxException e) {
			return false;
		}
	}

	static boolean isValue(String text) {
		try {
			RuntimeConfig.parseNbtValue(text);
			return true;
		} catch (CommandSyntaxException e) {
			return false;
		}
	}

	/** The ☰ button opening the list of the mob's NBT paths. */
	static Button pickerButton(Screen parent, ClientSession session, String entity, BiConsumer<String, @Nullable String> onPick) {
		return Widgets.button(Component.literal("☰"), 20, Component.translatable("controlmobspawns.gui.nbt_picker.open"),
			button -> Minecraft.getInstance().gui.setScreen(new NbtPathPickerScreen(parent, session, entity, onPick)));
	}
}
