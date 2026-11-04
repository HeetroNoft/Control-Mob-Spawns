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
import org.jspecify.annotations.Nullable;

/**
 * Replaces the spawn conditions of a mob. A new rule starts disabled so that it changes nothing
 * until enabled, with light and height ranges that exclude nothing.
 */
public class SpawnRuleScreen extends CmsScreen {
	private static final int MAX_LIGHT = 15;

	private final CmsConfig.MobSettings settings;
	private final CmsConfig.SpawnRule rule;

	public SpawnRuleScreen(Screen parent, ClientSession session, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.rule.title"));
		this.settings = settings;
		if (settings.spawnRule == null) {
			settings.spawnRule = new CmsConfig.SpawnRule();
			settings.spawnRule.enabled = false;
			settings.spawnRule.minBlockLight = 0;
			settings.spawnRule.maxBlockLight = MAX_LIGHT;
			settings.spawnRule.minSkyLight = 0;
			settings.spawnRule.maxSkyLight = MAX_LIGHT;
			settings.spawnRule.minY = session.minY;
			settings.spawnRule.maxY = session.maxY;
		}
		this.rule = settings.spawnRule;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.rule.hint");
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.enabled"))
			.add(Widgets.checkbox(rule.enabled, value -> rule.enabled = value)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.light")));
		rows.add(range("block_light", rule.minBlockLight, rule.maxBlockLight, value -> rule.minBlockLight = value, value -> rule.maxBlockLight = value));
		rows.add(range("sky_light", rule.minSkyLight, rule.maxSkyLight, value -> rule.minSkyLight = value, value -> rule.maxSkyLight = value));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.position")));
		rows.add(range("y", rule.minY, rule.maxY, value -> rule.minY = value, value -> rule.maxY = value));
		List<String> blocks = Stream.concat(
			BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted(),
			BuiltInRegistries.BLOCK.getTags().map(tag -> "#" + tag.key().location()).sorted()).toList();
		int blockCount = rule.blocksBelow.size();
		RowList.Row blocksRow = RowList.row(Component.translatable("controlmobspawns.gui.rule.blocks_below"))
			.info(Component.translatable("controlmobspawns.gui.rule.blocks_below.hint"))
			.hover(Widgets.button(Widgets.editLabel(blockCount), 80,
				button -> minecraft.gui.setScreen(new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.rule.blocks_below"),
					rule.blocksBelow, blocks))));
		rows.add(withClear(blocksRow, blockCount > 0, rule.blocksBelow::clear));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.environment")));
		rows.add(withClear(RowList.row(Component.translatable("controlmobspawns.gui.rule.time"))
			.rest(MobDetailScreen.value(timeName(rule.time), rule.time != CmsConfig.TimeOfDay.ANY))
			.hover(Widgets.cycle(List.of(CmsConfig.TimeOfDay.values()), rule.time, 100, SpawnRuleScreen::timeName, time -> {
				rule.time = time;
				refresh();
			})), rule.time != CmsConfig.TimeOfDay.ANY, () -> rule.time = CmsConfig.TimeOfDay.ANY));
		rows.add(withClear(RowList.row(Component.translatable("controlmobspawns.gui.rule.weather"))
			.rest(MobDetailScreen.value(weatherName(rule.weather), rule.weather != CmsConfig.Weather.ANY))
			.hover(Widgets.cycle(List.of(CmsConfig.Weather.values()), rule.weather, 100, SpawnRuleScreen::weatherName, weather -> {
				rule.weather = weather;
				refresh();
			})), rule.weather != CmsConfig.Weather.ANY, () -> rule.weather = CmsConfig.Weather.ANY));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.rule.moon"))
			.info(Component.translatable("controlmobspawns.gui.rule.moon.hint")));
		if (!rule.moonPhases.isEmpty()) {
			rows.add(withClear(RowList.row(Component.translatable("controlmobspawns.gui.rule.moon.any").withStyle(ChatFormatting.GRAY)), true,
				rule.moonPhases::clear));
		}
		for (MoonPhase phase : MoonPhase.values()) {
			int index = phase.index();
			rows.add(RowList.row(Component.translatable("controlmobspawns.moon." + phase.getSerializedName()))
				.add(Widgets.checkbox(rule.moonPhases.contains(index), selected -> {
					rule.moonPhases.remove(Integer.valueOf(index));
					if (selected) {
						rule.moonPhases.add(index);
					}
				})));
		}
		return rows;
	}

	/** A min/max row: two boxes, empty meaning no limit, and ↺ to empty both. */
	private RowList.Row range(String key, @Nullable Integer min, @Nullable Integer max, Consumer<@Nullable Integer> setMin, Consumer<@Nullable Integer> setMax) {
		RowList.Row row = RowList.row(Component.translatable("controlmobspawns.gui.rule." + key))
			.info(Component.translatable("controlmobspawns.gui.rule.range.hint"))
			.add(Widgets.optionalIntBox(min, 50, Component.translatable("controlmobspawns.gui.min"), setMin))
			.add(Widgets.optionalIntBox(max, 50, Component.translatable("controlmobspawns.gui.max"), setMax));
		return withClear(row, min != null || max != null, () -> {
			setMin.accept(null);
			setMax.accept(null);
		});
	}

	/** Adds the ↺ button emptying a condition, while it is set. */
	private RowList.Row withClear(RowList.Row row, boolean set, Runnable clear) {
		if (set) {
			row.fixed(Widgets.resetButton(Component.translatable("controlmobspawns.gui.rule.clear"), () -> {
				clear.run();
				refresh();
			}));
		}
		return row;
	}

	private static Component timeName(CmsConfig.TimeOfDay time) {
		return Component.translatable("controlmobspawns.time." + time.name().toLowerCase(Locale.ROOT));
	}

	private static Component weatherName(CmsConfig.Weather weather) {
		return Component.translatable("controlmobspawns.weather." + weather.name().toLowerCase(Locale.ROOT));
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.rule.delete"), 120, button -> {
			settings.spawnRule = null;
			minecraft.gui.setScreen(parent);
		}));
	}
}
