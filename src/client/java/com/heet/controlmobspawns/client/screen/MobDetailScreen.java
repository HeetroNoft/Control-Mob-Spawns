package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** The ⚙ sub-menu of a mob: each value as text, its control on hover. */
public class MobDetailScreen extends CmsScreen {
	/** Reasons that are not new spawns and cannot be disabled. */
	private static final List<EntitySpawnReason> REASONS = Arrays.stream(EntitySpawnReason.values())
		.filter(reason -> reason != EntitySpawnReason.LOAD && reason != EntitySpawnReason.DIMENSION_TRAVEL)
		.toList();

	private final String id;
	private final CmsConfig.MobSettings settings;
	private @Nullable Map<String, Integer> counts;

	public MobDetailScreen(Screen parent, ClientSession session, EntityType<?> type) {
		super(parent, session, Component.translatable("controlmobspawns.gui.mob.title", Mobs.name(type)));
		this.id = Mobs.id(type);
		this.settings = session.config.getOrCreateMob(id);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.spawn")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.spawn_rate"))
			.info(Component.translatable("controlmobspawns.gui.spawn_rate.hint"))
			.rest(value(Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(settings.spawnRate)), settings.spawnRate != 100.0))
			.hover(Widgets.positiveDoubleBox(settings.spawnRate, 60, Component.translatable("controlmobspawns.gui.spawn_rate"), value -> settings.spawnRate = value)));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mob.global_ban"))
			.rest(banState(settings.banned))
			.hover(Widgets.toggle(!settings.banned, 80, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					settings.banned = !allowed;
					refresh();
				})));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), null, settings.bannedDimensions.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), settings.bannedDimensions, session.dimensions)));
		List<String> biomeChoices = Stream.concat(session.biomes.stream(), session.biomeTags.stream().map(tag -> "#" + tag)).toList();
		rows.add(listRow(Component.translatable("controlmobspawns.gui.mob.banned_biomes"), Component.translatable("controlmobspawns.gui.ids.hint"),
			settings.bannedBiomes.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_biomes"), settings.bannedBiomes, biomeChoices)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.reasons")));
		for (EntitySpawnReason reason : REASONS) {
			String name = reason.name();
			boolean enabled = !settings.disabledReasons.contains(name);
			rows.add(RowList.row(reasonName(reason))
				.info(Component.translatable("controlmobspawns.reason." + name.toLowerCase(Locale.ROOT) + ".hint"))
				.rest(enabled
					? Component.translatable("controlmobspawns.gui.enabled").withStyle(ChatFormatting.GRAY)
					: Component.translatable("controlmobspawns.gui.disabled").withStyle(ChatFormatting.RED))
				.hover(Widgets.toggle(enabled, 80, Component.translatable("controlmobspawns.gui.enabled"), Component.translatable("controlmobspawns.gui.disabled"),
					value -> {
						settings.disabledReasons.remove(name);
						if (!value) {
							settings.disabledReasons.add(name);
						}
						refresh();
					})));
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.nbt")));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.nbt_bans.title"), Component.translatable("controlmobspawns.gui.nbt_bans.hint"),
			settings.nbtBans.size(), () -> new NbtBansScreen(this, session, id, settings)));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.nbt_proportions.title"), Component.translatable("controlmobspawns.gui.nbt_proportions.hint"),
			settings.nbtProportions.size(), () -> new NbtProportionsScreen(this, session, id, settings)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.rules")));
		boolean ruleActive = settings.spawnRule != null && settings.spawnRule.enabled;
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.title"))
			.info(Component.translatable("controlmobspawns.gui.rule.hint"))
			.rest(value(Component.translatable(ruleActive ? "controlmobspawns.gui.rule.active" : "controlmobspawns.gui.rule.none"), ruleActive))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.edit"), 80,
				button -> minecraft.gui.setScreen(new SpawnRuleScreen(this, session, settings)))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.despawn_mode"))
			.info(Component.translatable("controlmobspawns.gui.despawn_mode.hint"))
			.rest(value(despawnModeName(settings.despawn), settings.despawn != CmsConfig.DespawnMode.DEFAULT))
			.hover(Widgets.cycle(List.of(CmsConfig.DespawnMode.values()), settings.despawn, 100, MobDetailScreen::despawnModeName,
				mode -> {
					settings.despawn = mode;
					refresh();
				})));

		if (session.remote) {
			rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn_now"))
				.info(Component.translatable("controlmobspawns.gui.mob.despawn_now.hint")));
			if (counts == null) {
				rows.add(RowList.note(Component.translatable("controlmobspawns.gui.loading")));
			} else {
				int total = counts.values().stream().mapToInt(Integer::intValue).sum();
				rows.add(despawnRow(Component.translatable("controlmobspawns.gui.current.all_dimensions"), "", total));
				counts.forEach((dimension, count) -> rows.add(despawnRow(Component.literal(dimension), dimension, count)));
			}
		}
		return rows;
	}

	/** A row opening a list editor; the button shows the number of entries. */
	private RowList.Row listRow(Component label, @Nullable Component info, int count, Supplier<Screen> screen) {
		return RowList.row(label).info(info)
			.hover(Widgets.button(Widgets.editLabel(count), 80, button -> minecraft.gui.setScreen(screen.get())));
	}

	private RowList.Row despawnRow(Component where, String dimension, int count) {
		return RowList.row(where)
			.rest(Component.literal(Integer.toString(count)).withStyle(ChatFormatting.GRAY))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.current.despawn_all"), 80,
				button -> ClientPlayNetworking.send(new CmsPayloads.DespawnAll(id, dimension))));
	}

	/** A value at rest: gray when vanilla, yellow when changed. */
	static Component value(Component text, boolean modified) {
		return text.copy().withStyle(modified ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
	}

	private static Component banState(boolean banned) {
		return banned
			? Component.translatable("controlmobspawns.gui.banned").withStyle(ChatFormatting.RED)
			: Component.translatable("controlmobspawns.gui.allowed").withStyle(ChatFormatting.GRAY);
	}

	private static Component despawnModeName(CmsConfig.DespawnMode mode) {
		return Component.translatable("controlmobspawns.despawn_mode." + mode.name().toLowerCase(Locale.ROOT));
	}

	/** Counts of this mob per dimension; the server sends them again after each despawn. */
	@Override
	public void added() {
		super.added();
		if (session.remote) {
			ControlMobSpawnsClient.setMobCountListener(received -> {
				Map<String, Integer> perDimension = new TreeMap<>();
				for (CmsPayloads.MobCount count : received) {
					if (count.entity().equals(id)) {
						perDimension.merge(count.dimension(), count.count(), Integer::sum);
					}
				}
				counts = perDimension;
				refresh();
			});
			ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts());
		}
	}

	@Override
	public void removed() {
		super.removed();
		ControlMobSpawnsClient.setMobCountListener(null);
	}

	public static Component reasonName(EntitySpawnReason reason) {
		return Component.translatable("controlmobspawns.reason." + reason.name().toLowerCase(Locale.ROOT));
	}
}
