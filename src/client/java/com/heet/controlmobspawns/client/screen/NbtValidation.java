package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.heet.controlmobspawns.client.NbtSuggestions;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

	/** A path box suggesting the mob's NBT paths; its hint replaces a column title. */
	static EditBox pathBox(CmsScreen screen, String entity, String value, int width, Consumer<String> onValid) {
		EditBox box = Widgets.textBox(value, width, Component.translatable("controlmobspawns.gui.nbt.path"), NbtValidation::isPath, onValid);
		box.setHint(Component.translatable("controlmobspawns.gui.nbt.path").withStyle(ChatFormatting.DARK_GRAY));
		return screen.autocomplete(box, () -> NbtSuggestions.paths(entity));
	}

	/** A value box suggesting the values seen at the current path. */
	static EditBox valueBox(CmsScreen screen, String entity, String value, int width, Supplier<String> path, Consumer<String> onValid) {
		EditBox box = Widgets.textBox(value, width, Component.translatable("controlmobspawns.gui.nbt.value"), NbtValidation::isValue, onValid);
		box.setHint(Component.translatable("controlmobspawns.gui.nbt.value").withStyle(ChatFormatting.DARK_GRAY));
		return screen.autocomplete(box, () -> NbtSuggestions.values(entity, path.get()));
	}

	/** The ☰ button opening the list of the mob's NBT paths. */
	static Button pickerButton(Screen parent, ClientSession session, String entity, BiConsumer<String, @Nullable String> onPick) {
		return Widgets.button(Component.literal("☰"), 20, Component.translatable("controlmobspawns.gui.nbt_picker.open"),
			button -> Minecraft.getInstance().gui.setScreen(new NbtPathPickerScreen(parent, session, entity, onPick)));
	}
}
