package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.RuntimeConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;

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
}
