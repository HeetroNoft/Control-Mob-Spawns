package com.heet.controlmobspawns.client.screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Small factories for the widgets used in rows. */
public final class Widgets {
	private static final int VALID_COLOR = 0xFFE0E0E0;
	private static final int INVALID_COLOR = 0xFFFF5555;

	private Widgets() {
	}

	public static Button button(Component text, int width, Button.OnPress onPress) {
		return Button.builder(text, onPress).width(width).build();
	}

	public static Button button(Component text, int width, @Nullable Component tooltip, Button.OnPress onPress) {
		Button button = button(text, width, onPress);
		if (tooltip != null) {
			button.setTooltip(Tooltip.create(tooltip));
		}
		return button;
	}

	public static CycleButton<Boolean> toggle(boolean value, int width, Component on, Component off, Consumer<Boolean> onChange) {
		return CycleButton.booleanBuilder(on.copy().withStyle(ChatFormatting.GREEN), off.copy().withStyle(ChatFormatting.RED), value)
			.displayOnlyValue()
			.create(0, 0, width, 20, Component.empty(), (button, newValue) -> onChange.accept(newValue));
	}

	public static CycleButton<Boolean> onOff(boolean value, int width, Consumer<Boolean> onChange) {
		return toggle(value, width, Component.translatable("options.on"), Component.translatable("options.off"), onChange);
	}

	public static <T> CycleButton<T> cycle(List<T> values, T value, int width, Function<T, Component> names, Consumer<T> onChange) {
		return CycleButton.builder(names, value)
			.withValues(values)
			.displayOnlyValue()
			.create(0, 0, width, 20, Component.empty(), (button, newValue) -> onChange.accept(newValue));
	}

	/** A text box that shows its text in red while {@code valid} rejects it, and reports valid values. */
	public static EditBox textBox(String value, int width, Component narration, Predicate<String> valid, Consumer<String> onValid) {
		EditBox box = new EditBox(Minecraft.getInstance().font, width, 18, narration);
		box.setMaxLength(1024);
		box.setValue(value);
		box.setTextColor(valid.test(value) ? VALID_COLOR : INVALID_COLOR);
		box.setResponder(text -> {
			boolean ok = valid.test(text);
			box.setTextColor(ok ? VALID_COLOR : INVALID_COLOR);
			if (ok) {
				onValid.accept(text);
			}
		});
		return box;
	}

	public static EditBox textBox(String value, int width, Component narration, Consumer<String> onChange) {
		return textBox(value, width, narration, text -> true, onChange);
	}

	/** An integer box; an empty box means "no value" and reports null. */
	public static EditBox optionalIntBox(@Nullable Integer value, int width, Component narration, Consumer<@Nullable Integer> onChange) {
		EditBox box = textBox(value == null ? "" : value.toString(), width, narration,
			text -> text.isBlank() || parseInt(text) != null,
			text -> onChange.accept(text.isBlank() ? null : parseInt(text)));
		box.setHint(Component.literal("—").withStyle(ChatFormatting.DARK_GRAY));
		return box;
	}

	public static EditBox intBox(int value, int width, Component narration, int min, Consumer<Integer> onChange) {
		return textBox(Integer.toString(value), width, narration,
			text -> parseInt(text) != null && parseInt(text) >= min,
			text -> onChange.accept(parseInt(text)));
	}

	/** A number box for values strictly above zero. */
	public static EditBox positiveDoubleBox(double value, int width, Component narration, Consumer<Double> onChange) {
		return textBox(formatNumber(value), width, narration,
			text -> parseDouble(text) != null && parseDouble(text) > 0,
			text -> onChange.accept(parseDouble(text)));
	}

	public static EditBox percentBox(double value, int width, Component narration, Consumer<Double> onChange) {
		return textBox(formatNumber(value), width, narration,
			text -> parseDouble(text) != null && parseDouble(text) >= 0 && parseDouble(text) <= 100,
			text -> onChange.accept(parseDouble(text)));
	}

	public static String formatNumber(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
	}

	public static @Nullable Integer parseInt(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static @Nullable Double parseDouble(String text) {
		try {
			double value = Double.parseDouble(text.trim().replace(',', '.'));
			return Double.isFinite(value) ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
