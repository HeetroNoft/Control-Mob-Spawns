package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.ControlMobSpawns;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** A square button showing a 12×12 icon from the mod's GUI sprites, such as the reset arrow. */
public class IconButton extends AbstractButton {
	public static final Identifier RESET = ControlMobSpawns.id("reset");
	private static final int ICON_SIZE = 12;

	private final Identifier icon;
	private final Runnable onPress;

	public IconButton(Identifier icon, Component narration, Runnable onPress) {
		super(0, 0, 20, 20, narration);
		this.icon = icon;
		this.onPress = onPress;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractDefaultSprite(graphics);
		int offset = (getWidth() - ICON_SIZE) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, getX() + offset, getY() + offset, ICON_SIZE, ICON_SIZE, active ? 0xFFFFFFFF : 0xFFA0A0A0);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
