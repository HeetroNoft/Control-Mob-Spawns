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

/**
 * A scrolling list of borderless rows: an optional icon, a label with an optional ⓘ, and widgets
 * on the right. Hover-only widgets keep their place when hidden so that nothing moves.
 */
public class RowList extends ContainerObjectSelectionList<RowList.Row> {
	private static final int ROW_HEIGHT = 24;
	private static final int GAP = 4;
	private static final int TOOLTIP_WIDTH = 250;
	private static final String INFO = "ⓘ";

	public RowList(Minecraft minecraft, int width) {
		super(minecraft, width, 0, 0, ROW_HEIGHT);
	}

	public void setRows(List<Row> rows) {
		double scroll = scrollAmount();
		replaceEntries(rows);
		setScrollAmount(scroll);
	}

	/** The row width for a screen width, shared with the toolbars aligned on the rows. */
	public static int rowWidth(int screenWidth) {
		return Math.min(screenWidth - 40, 460);
	}

	@Override
	public int getRowWidth() {
		return rowWidth(width);
	}

	public static Row row(Component label) {
		return new Row(label, false);
	}

	/** A gray line of text, for column names or notes. */
	public static Row note(Component text) {
		return new Row(text.copy().withStyle(ChatFormatting.GRAY), false);
	}

	public static Row header(Component text) {
		return new Row(text.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
	}

	public static class Row extends ContainerObjectSelectionList.Entry<Row> {
		private record Slot(AbstractWidget widget, boolean hoverOnly) {
		}

		private final Component label;
		private final boolean header;
		private final List<Slot> slots = new ArrayList<>();
		private @Nullable ItemStack icon;
		private @Nullable Component info;
		private @Nullable Component rest;
		private boolean keepHover;

		Row(Component label, boolean header) {
			this.label = label;
			this.header = header;
		}

		public Component label() {
			return label;
		}

		public Row icon(@Nullable ItemStack stack) {
			this.icon = stack;
			return this;
		}

		/** An explanation shown when hovering the ⓘ after the label. */
		public Row info(@Nullable Component text) {
			this.info = text;
			return this;
		}

		/** Text shown on the right while the row is not hovered, in place of the hover-only widgets. */
		public Row rest(@Nullable Component text) {
			this.rest = text;
			return this;
		}

		/**
		 * Keeps a single hover-only action hidden until hover. By default a row with only one
		 * hover-only action shows it all the time: hover only declutters rows with several actions.
		 */
		public Row keepHover() {
			this.keepHover = true;
			return this;
		}

		/** Adds a widget that is always visible; widgets are laid out left to right. */
		public Row add(AbstractWidget widget) {
			slots.add(new Slot(widget, false));
			return this;
		}

		/** Adds a widget that is only visible while the row is hovered or one of its widgets has focus. */
		public Row hover(AbstractWidget widget) {
			slots.add(new Slot(widget, true));
			return this;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			Font font = Minecraft.getInstance().font;
			int textY = getContentYMiddle() - font.lineHeight / 2;
			if (header) {
				graphics.centeredText(font, label, getContentXMiddle(), textY, -1);
				if (info != null) {
					extractInfo(graphics, font, getContentXMiddle() + font.width(label) / 2 + 4, textY, mouseX, mouseY);
				}
				return;
			}
			boolean singleAction = !keepHover && slots.stream().filter(Slot::hoverOnly).count() == 1;
			boolean showHover = singleAction || hovered || slots.stream().anyMatch(slot -> slot.hoverOnly() && slot.widget().isFocused());
			int right = getContentRight();
			for (int i = slots.size() - 1; i >= 0; i--) {
				AbstractWidget widget = slots.get(i).widget();
				widget.setPosition(right - widget.getWidth(), getContentYMiddle() - widget.getHeight() / 2);
				widget.visible = !slots.get(i).hoverOnly() || showHover;
				widget.extractRenderState(graphics, mouseX, mouseY, a);
				right -= widget.getWidth() + GAP;
			}
			if (!showHover && rest != null) {
				graphics.text(font, rest, getContentRight() - font.width(rest), textY, -1);
				right = Math.min(right, getContentRight() - font.width(rest) - GAP);
			}
			int x = getContentX();
			if (icon != null && !icon.isEmpty()) {
				graphics.item(icon, x, getContentYMiddle() - 8);
				x += 20;
			}
			int available = right - x - (info != null ? font.width(INFO) + 4 : 0);
			String text = label.getString();
			if (font.width(text) > available) {
				text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
			}
			graphics.text(font, Component.literal(text).withStyle(label.getStyle()), x, textY, -1);
			if (info != null) {
				extractInfo(graphics, font, x + font.width(text) + 4, textY, mouseX, mouseY);
			}
		}

		private void extractInfo(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
			boolean over = mouseX >= x - 1 && mouseX < x + font.width(INFO) + 1 && mouseY >= y - 2 && mouseY < y + font.lineHeight + 2;
			graphics.text(font, INFO, x, y, over ? 0xFFFFFFFF : 0xFF808080);
			if (over) {
				graphics.setTooltipForNextFrame(font, font.split(info, TOOLTIP_WIDTH), mouseX, mouseY);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return slots.stream().map(Slot::widget).toList();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return slots.stream().map(Slot::widget).toList();
		}
	}
}
