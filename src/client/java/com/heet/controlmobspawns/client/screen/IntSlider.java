package com.heet.controlmobspawns.client.screen;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over whole numbers from {@code min} to {@code max}, snapping to each step. */
public class IntSlider extends AbstractSliderButton {
	private final int min;
	private final int max;
	private final IntFunction<Component> label;
	private final IntConsumer onChange;

	public IntSlider(int width, int min, int max, int initial, IntFunction<Component> label, IntConsumer onChange) {
		super(0, 0, width, 20, label.apply(initial), (double) (initial - min) / (max - min));
		this.min = min;
		this.max = max;
		this.label = label;
		this.onChange = onChange;
	}

	public int intValue() {
		return min + (int) Math.round(value * (max - min));
	}

	public void setIntValue(int newValue) {
		value = (double) (Math.clamp(newValue, min, max) - min) / (max - min);
		updateMessage();
		applyValue();
	}

	@Override
	protected void updateMessage() {
		setMessage(label.apply(intValue()));
	}

	/** Snaps the handle to the nearest step and reports it. */
	@Override
	protected void applyValue() {
		value = (double) (intValue() - min) / (max - min);
		onChange.accept(intValue());
	}
}
