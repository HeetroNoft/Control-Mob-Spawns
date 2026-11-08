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
	/** Rows with fewer hover-only actions show them all the time. */
	private static final int HOVER_FROM = 3;

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
		private record Slot(AbstractWidget widget, boolean hoverOnly, boolean fixed) {
		}

		private final Component label;
		private final boolean header;
		private final List<Slot> slots = new ArrayList<>();
		private @Nullable ItemStack icon;
		private @Nullable Component info;
		private @Nullable Component rest;
		private @Nullable Component badge;
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

		/** Text always shown, after the resting text or before the widgets: where a value comes from. */
		public Row badge(@Nullable Component text) {
			this.badge = text;
			return this;
		}

		/**
		 * Keeps hover-only actions hidden until hover. By default a row with fewer than three
		 * shows them all the time: hover only declutters rows with many actions.
		 */
		public Row keepHover() {
			this.keepHover = true;
			return this;
		}

		/** Adds a widget that is always visible; widgets are laid out left to right. */
		public Row add(AbstractWidget widget) {
			slots.add(new Slot(widget, false, false));
			return this;
		}

		/**
		 * Adds a widget that is always visible at the far right, such as a reset button. It does not
		 * change how the other widgets show, so a row looks the same with or without it.
		 */
		public Row fixed(AbstractWidget widget) {
			slots.add(new Slot(widget, false, true));
			return this;
		}

		/** Adds a widget that is only visible while the row is hovered or one of its widgets has focus. */
		public Row hover(AbstractWidget widget) {
			slots.add(new Slot(widget, true, false));
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
			boolean fewActions = !keepHover && slots.stream().filter(Slot::hoverOnly).count() < HOVER_FROM;
			boolean showHover = fewActions || hovered || slots.stream().anyMatch(slot -> slot.hoverOnly() && slot.widget().isFocused());
			int right = getContentRight();
			for (int i = slots.size() - 1; i >= 0; i--) {
				if (slots.get(i).fixed()) {
					right = place(slots.get(i).widget(), right, true, graphics, mouseX, mouseY, a);
				}
			}
			int end = right;
			for (int i = slots.size() - 1; i >= 0; i--) {
				Slot slot = slots.get(i);
				if (!slot.fixed()) {
					right = place(slot.widget(), right, !slot.hoverOnly() || showHover, graphics, mouseX, mouseY, a);
				}
			}
			if (!showHover && rest != null) {
				Component text = badge == null ? rest : Component.empty().append(rest).append("  ").append(badge);
				graphics.text(font, text, end - font.width(text), textY, -1);
				right = Math.min(right, end - font.width(text) - GAP);
			} else if (badge != null) {
				graphics.text(font, badge, right - font.width(badge), textY, -1);
				right -= font.width(badge) + GAP;
			}
			int x = getContentX();
			if (icon != null && !icon.isEmpty()) {
				graphics.item(icon, x, getContentYMiddle() - 8);
				x += 20;
			}
			int available = right - x - (info != null ? font.width(INFO) + 4 : 0);
			// A label that fits keeps the styles of its parts; a cut one takes the style of the whole.
			Component shown = label;
			if (font.width(label) > available) {
				String text = font.plainSubstrByWidth(label.getString(), Math.max(0, available - font.width("…"))) + "…";
				shown = Component.literal(text).withStyle(label.getStyle());
			}
			graphics.text(font, shown, x, textY, -1);
			if (info != null) {
				extractInfo(graphics, font, x + font.width(shown) + 4, textY, mouseX, mouseY);
			}
		}

		/** Places a widget ending at {@code right} and returns where the next one ends. */
		private int place(AbstractWidget widget, int right, boolean visible, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			widget.setPosition(right - widget.getWidth(), getContentYMiddle() - widget.getHeight() / 2);
			widget.visible = visible;
			widget.extractRenderState(graphics, mouseX, mouseY, a);
			return right - widget.getWidth() - GAP;
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
