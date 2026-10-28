package com.heet.controlmobspawns.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A flat tab: plain text, underlined when selected. */
public class TabButton extends AbstractButton {
	private static final int PADDING = 12;

	private final boolean selected;
	private final Runnable onSelect;

	public TabButton(Component text, boolean selected, Runnable onSelect) {
		super(0, 0, Minecraft.getInstance().font.width(text) + PADDING, 16, text);
		this.selected = selected;
		this.onSelect = onSelect;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (!selected) {
			onSelect.run();
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		int color = selected || isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF909090;
		graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - font.lineHeight) / 2, color);
		if (selected) {
			graphics.fill(getX() + PADDING / 2 - 2, getY() + getHeight() - 2, getX() + getWidth() - PADDING / 2 + 2, getY() + getHeight() - 1, 0xFFFFFFFF);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
