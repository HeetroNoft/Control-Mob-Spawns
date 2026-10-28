package com.heet.controlmobspawns.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** A scrolling list of rows: an optional icon, a label, and widgets aligned to the right. */
public class RowList extends ContainerObjectSelectionList<RowList.Row> {
	private static final int ROW_HEIGHT = 24;
	private static final int GAP = 4;
	private static final int TOOLTIP_WIDTH = 250;

	public RowList(Minecraft minecraft, int width) {
		super(minecraft, width, 0, 0, ROW_HEIGHT);
	}

	public void setRows(List<Row> rows) {
		double scroll = scrollAmount();
		replaceEntries(rows);
		setScrollAmount(scroll);
	}

	@Override
	public int getRowWidth() {
		return Math.min(width - 40, 460);
	}

	public static Row header(Component text) {
		return new Row(text.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), null, null, List.of(), true);
	}

	public static Row row(Component label, AbstractWidget... widgets) {
		return new Row(label, null, null, List.of(widgets), false);
	}

	public static Row row(Component label, @Nullable ItemStack icon, @Nullable Component hint, AbstractWidget... widgets) {
		return new Row(label, icon, hint, List.of(widgets), false);
	}

	public static class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final Component label;
		private final @Nullable ItemStack icon;
		private final @Nullable Component hint;
		private final List<AbstractWidget> widgets;
		private final boolean header;

		Row(Component label, @Nullable ItemStack icon, @Nullable Component hint, List<AbstractWidget> widgets, boolean header) {
			this.label = label;
			this.icon = icon;
			this.hint = hint;
			this.widgets = new ArrayList<>(widgets);
			this.header = header;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			Font font = Minecraft.getInstance().font;
			int textY = getContentYMiddle() - font.lineHeight / 2;
			if (header) {
				graphics.centeredText(font, label, getContentXMiddle(), textY, -1);
				return;
			}
			int right = getContentRight();
			for (int i = widgets.size() - 1; i >= 0; i--) {
				AbstractWidget widget = widgets.get(i);
				widget.setPosition(right - widget.getWidth(), getContentYMiddle() - widget.getHeight() / 2);
				widget.extractRenderState(graphics, mouseX, mouseY, a);
				right -= widget.getWidth() + GAP;
			}
			int x = getContentX();
			if (icon != null) {
				graphics.item(icon, x, getContentYMiddle() - 8);
				x += 20;
			}
			int available = right - x;
			String text = label.getString();
			if (font.width(text) > available) {
				text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
			}
			graphics.text(font, Component.literal(text).withStyle(label.getStyle()), x, textY, -1);
			if (hint != null && hovered && mouseX >= x && mouseX < right) {
				graphics.setTooltipForNextFrame(font, font.split(hint, TOOLTIP_WIDTH), mouseX, mouseY);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
		}
	}
}
