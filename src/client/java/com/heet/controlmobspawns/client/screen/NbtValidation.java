package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.heet.controlmobspawns.client.NbtSuggestions;
import java.util.List;
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
	private static final int NORMAL_COLOR = 0xFFE0E0E0;
	private static final int PARTIAL_COLOR = 0xFFFFAA00;
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

	/** A path box suggesting the NBT paths of the mobs; its hint replaces a column title. */
	static EditBox pathBox(CmsScreen screen, List<String> entities, String value, int width, Consumer<String> onValid) {
		EditBox box = Widgets.textBox(value, width, Component.translatable("controlmobspawns.gui.nbt.path"), NbtValidation::isPath, onValid);
		box.setHint(Component.translatable("controlmobspawns.gui.nbt.path").withStyle(ChatFormatting.DARK_GRAY));
		// For a group, paths that only some of its mobs have are orange: a setting on them skips the others.
		return screen.autocomplete(box, () -> NbtSuggestions.paths(entities), path -> {
			int mobs = NbtSuggestions.shared(entities).stream().filter(info -> info.path().equals(path)).findFirst()
				.map(NbtSuggestions.SharedPath::mobs).orElse(entities.size());
			return mobs < entities.size() ? PARTIAL_COLOR : NORMAL_COLOR;
		});
	}

	/** A value box suggesting the values seen at the current path. */
	static EditBox valueBox(CmsScreen screen, List<String> entities, String value, int width, Supplier<String> path, Consumer<String> onValid) {
		EditBox box = Widgets.textBox(value, width, Component.translatable("controlmobspawns.gui.nbt.value"), NbtValidation::isValue, onValid);
		box.setHint(Component.translatable("controlmobspawns.gui.nbt.value").withStyle(ChatFormatting.DARK_GRAY));
		return screen.autocomplete(box, () -> NbtSuggestions.values(entities, path.get()));
	}

	/** The ☰ button opening the list of the mobs' NBT paths. */
	static Button pickerButton(Screen parent, ClientSession session, List<String> entities, BiConsumer<String, @Nullable String> onPick) {
		return Widgets.button(Component.literal("☰"), 20, Component.translatable("controlmobspawns.gui.nbt_picker.open"),
			button -> Minecraft.getInstance().gui.setScreen(new NbtPathPickerScreen(parent, session, entities, onPick)));
	}
}
