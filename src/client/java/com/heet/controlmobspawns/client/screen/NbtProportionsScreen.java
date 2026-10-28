package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.NbtSuggestions;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Forces the share of spawned mobs that get each NBT value. */
public class NbtProportionsScreen extends CmsScreen {
	private final String entity;
	private final CmsConfig.MobSettings settings;

	public NbtProportionsScreen(Screen parent, ClientSession session, String entity, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_proportions.title"));
		this.entity = entity;
		this.settings = settings;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.nbt_proportions.hint")
			.append("\n\n").append(Component.translatable("controlmobspawns.gui.nbt_proportions.limit.hint"));
	}

	@Override
	public void added() {
		super.added();
		NbtSuggestions.request(entity);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		Map<String, Double> totals = new LinkedHashMap<>();
		for (CmsConfig.NbtProportion proportion : settings.nbtProportions) {
			totals.merge(proportion.path, proportion.percent, Double::sum);
		}
		totals.forEach((path, total) -> {
			if (total > 100.0) {
				rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_proportions.over", path, Widgets.formatNumber(total))
					.withStyle(ChatFormatting.RED)));
			}
		});
		if (settings.nbtProportions.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.nbt.empty")));
		}
		for (CmsConfig.NbtProportion proportion : List.copyOf(settings.nbtProportions)) {
			EditBox percent = Widgets.percentBox(proportion.percent, 45, Component.translatable("controlmobspawns.gui.nbt.percent"), value -> proportion.percent = value);
			percent.setHint(Component.literal("%").withStyle(ChatFormatting.DARK_GRAY));
			rows.add(RowList.row(Component.empty())
				.add(NbtValidation.pathBox(this, entity, proportion.path, 130, value -> proportion.path = value))
				.hover(NbtValidation.pickerButton(this, session, entity, (path, value) -> {
					proportion.path = path;
					if (value != null) {
						proportion.value = value;
					}
				}))
				.add(NbtValidation.valueBox(this, entity, proportion.value, 90, () -> proportion.path, value -> proportion.value = value))
				.add(percent)
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					settings.nbtProportions.remove(proportion);
					refresh();
				})));
		}
		return rows;
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.add"), 100, button -> {
			settings.nbtProportions.add(new CmsConfig.NbtProportion());
			refresh();
		}));
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.nbt_proportions.check"), 100,
			Component.translatable("controlmobspawns.gui.nbt_proportions.check.hint"), button -> refresh()));
	}
}
