package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.MoonPhase;

/** Replaces the spawn conditions of a mob. A new rule starts disabled so that it changes nothing until enabled. */
public class SpawnRuleScreen extends CmsScreen {
	private final CmsConfig.MobSettings settings;
	private final CmsConfig.SpawnRule rule;

	public SpawnRuleScreen(Screen parent, ClientSession session, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.rule.title"));
		this.settings = settings;
		if (settings.spawnRule == null) {
			settings.spawnRule = new CmsConfig.SpawnRule();
			settings.spawnRule.enabled = false;
		}
		this.rule = settings.spawnRule;
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.help").withStyle(ChatFormatting.GRAY), null,
			Component.translatable("controlmobspawns.gui.rule.hint")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.enabled"),
			Widgets.onOff(rule.enabled, 80, value -> rule.enabled = value)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.light")));
		rows.add(range("block_light", rule.minBlockLight, rule.maxBlockLight, value -> rule.minBlockLight = value, value -> rule.maxBlockLight = value));
		rows.add(range("sky_light", rule.minSkyLight, rule.maxSkyLight, value -> rule.minSkyLight = value, value -> rule.maxSkyLight = value));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.position")));
		rows.add(range("y", rule.minY, rule.maxY, value -> rule.minY = value, value -> rule.maxY = value));
		List<String> blocks = Stream.concat(
			BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted(),
			BuiltInRegistries.BLOCK.getTags().map(tag -> "#" + tag.key().location()).sorted()).toList();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.blocks_below"), null,
			Component.translatable("controlmobspawns.gui.rule.blocks_below.hint"),
			Widgets.button(Component.translatable("controlmobspawns.gui.edit_count", rule.blocksBelow.size()), 100,
				button -> minecraft.gui.setScreen(new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.rule.blocks_below"),
					rule.blocksBelow, blocks)))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.environment")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.time"),
			Widgets.cycle(List.of(CmsConfig.TimeOfDay.values()), rule.time, 100,
				time -> Component.translatable("controlmobspawns.time." + time.name().toLowerCase(Locale.ROOT)), time -> rule.time = time)));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.weather"),
			Widgets.cycle(List.of(CmsConfig.Weather.values()), rule.weather, 100,
				weather -> Component.translatable("controlmobspawns.weather." + weather.name().toLowerCase(Locale.ROOT)), weather -> rule.weather = weather)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.moon")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.moon.hint").withStyle(ChatFormatting.GRAY)));
		for (MoonPhase phase : MoonPhase.values()) {
			int index = phase.index();
			rows.add(RowList.row(Component.translatable("controlmobspawns.moon." + phase.getSerializedName()),
				Widgets.onOff(rule.moonPhases.contains(index), 80, selected -> {
					rule.moonPhases.remove(Integer.valueOf(index));
					if (selected) {
						rule.moonPhases.add(index);
					}
				})));
		}
		return rows;
	}

	private RowList.Row range(String key, Integer min, Integer max,
		Consumer<Integer> setMin, Consumer<Integer> setMax) {
		return RowList.row(Component.translatable("controlmobspawns.gui.rule." + key), null,
			Component.translatable("controlmobspawns.gui.rule.range.hint"),
			Widgets.optionalIntBox(min, 50, Component.translatable("controlmobspawns.gui.min"), setMin),
			Widgets.optionalIntBox(max, 50, Component.translatable("controlmobspawns.gui.max"), setMax));
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.rule.delete"), 100, button -> {
			settings.spawnRule = null;
			minecraft.gui.setScreen(parent);
		}));
	}
}
