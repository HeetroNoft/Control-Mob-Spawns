package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Forces the share of spawned mobs that get each NBT value. */
public class NbtProportionsScreen extends CmsScreen {
	private final CmsConfig.MobSettings settings;

	public NbtProportionsScreen(Screen parent, ClientSession session, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_proportions.title"));
		this.settings = settings;
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.help").withStyle(ChatFormatting.GRAY), null,
			Component.translatable("controlmobspawns.gui.nbt_proportions.hint")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_proportions.limit").withStyle(ChatFormatting.GOLD), null,
			Component.translatable("controlmobspawns.gui.nbt_proportions.limit.hint")));
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
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_proportions.columns").withStyle(ChatFormatting.GRAY)));
		for (CmsConfig.NbtProportion proportion : List.copyOf(settings.nbtProportions)) {
			rows.add(RowList.row(Component.empty(),
				Widgets.textBox(proportion.path, 130, Component.translatable("controlmobspawns.gui.nbt.path"), NbtValidation::isPath, value -> proportion.path = value),
				Widgets.textBox(proportion.value, 90, Component.translatable("controlmobspawns.gui.nbt.value"), NbtValidation::isValue, value -> proportion.value = value),
				Widgets.percentBox(proportion.percent, 45, Component.translatable("controlmobspawns.gui.nbt.percent"), value -> proportion.percent = value),
				Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
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
