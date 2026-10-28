package com.heet.controlmobspawns.client.screen;

import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A mob of the "Current mobs" tab on a single fixed-height row. The option value is the ban;
 * hovering the row shows the Despawn, Allowed/Banned and ⚙ buttons in place of the ban state.
 */
public class MobRowController implements Controller<Boolean> {
	private final Option<Boolean> option;
	private final Consumer<YACLScreen> onDespawn;
	private final Consumer<YACLScreen> onSettings;

	public MobRowController(Option<Boolean> option, Consumer<YACLScreen> onDespawn, Consumer<YACLScreen> onSettings) {
		this.option = option;
		this.onDespawn = onDespawn;
		this.onSettings = onSettings;
	}

	@Override
	public Option<Boolean> option() {
		return option;
	}

	@Override
	public Component formatValue() {
		return banState(option.pendingValue());
	}

	private static Component banState(boolean banned) {
		return banned
			? Component.translatable("controlmobspawns.gui.banned").withStyle(ChatFormatting.RED)
			: Component.translatable("controlmobspawns.gui.allowed").withStyle(ChatFormatting.GREEN);
	}

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
		return new Widget(this, screen, widgetDimension);
	}

	private static class Widget extends ControllerWidget<MobRowController> {
		private static final int GAP = 2;
		private static final int PADDING = 8;

		private record RowButton(Component text, int x1, int x2, Consumer<YACLScreen> action) {
		}

		Widget(MobRowController control, YACLScreen screen, Dimension<Integer> dim) {
			super(control, screen, dim);
		}

		/** Buttons from left to right, laid out against the right edge of the row. */
		private List<RowButton> buttons() {
			Component despawn = Component.translatable("controlmobspawns.gui.current.despawn_all");
			Component ban = banState(control.option().pendingValue());
			Component settings = Component.literal("⚙");
			int banWidth = Math.max(textRenderer.width(banState(true)), textRenderer.width(banState(false))) + PADDING;
			int settingsWidth = textRenderer.width(settings) + PADDING;
			int despawnWidth = textRenderer.width(despawn) + PADDING;
			int right = getDimension().xLimit() - getXPadding();
			int settingsX = right - settingsWidth;
			int banX = settingsX - GAP - banWidth;
			int despawnX = banX - GAP - despawnWidth;
			return List.of(
				new RowButton(despawn, despawnX, banX - GAP, control.onDespawn),
				new RowButton(ban, banX, settingsX - GAP, screen -> control.option().requestSet(!control.option().pendingValue())),
				new RowButton(settings, settingsX, right, control.onSettings));
		}

		@Override
		protected int getHoveredControlWidth() {
			List<RowButton> buttons = buttons();
			return buttons.getLast().x2() - buttons.getFirst().x1();
		}

		@Override
		protected int getUnhoveredControlWidth() {
			return textRenderer.width(getValueText());
		}

		@Override
		protected void extractValueText(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			if (!isHovered()) {
				super.extractValueText(graphics, mouseX, mouseY, delta);
				return;
			}
			int y1 = getDimension().y() + 2;
			int y2 = getDimension().yLimit() - 2;
			for (RowButton button : buttons()) {
				boolean over = mouseX >= button.x1() && mouseX < button.x2() && mouseY >= y1 && mouseY < y2;
				drawButtonRect(graphics, button.x1(), y1, button.x2(), y2, over, isAvailable());
				graphics.centeredText(textRenderer, button.text(), (button.x1() + button.x2()) / 2, getTextY(), -1);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (!isAvailable() || !getDimension().isPointInside((int) event.x(), (int) event.y())) {
				return false;
			}
			for (RowButton button : buttons()) {
				if (event.x() >= button.x1() && event.x() < button.x2()) {
					playDownSound();
					button.action().accept(screen);
					return true;
				}
			}
			return false;
		}
	}
}
