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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** The ⚙ sub-menu of a mob. */
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
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.spawn_rate"), null,
			Component.translatable("controlmobspawns.gui.spawn_rate.hint"),
			Widgets.positiveDoubleBox(settings.spawnRate, 60, Component.translatable("controlmobspawns.gui.spawn_rate"), value -> settings.spawnRate = value)));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mob.global_ban"),
			Widgets.toggle(!settings.banned, 80, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> settings.banned = !allowed)));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mob.banned_dimensions"),
			editButton(settings.bannedDimensions.size(), () -> new IdListScreen(this, session,
				Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), settings.bannedDimensions, session.dimensions))));
		List<String> biomeChoices = Stream.concat(session.biomes.stream(), session.biomeTags.stream().map(tag -> "#" + tag)).toList();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mob.banned_biomes"), null,
			Component.translatable("controlmobspawns.gui.ids.hint"),
			editButton(settings.bannedBiomes.size(), () -> new IdListScreen(this, session,
				Component.translatable("controlmobspawns.gui.mob.banned_biomes"), settings.bannedBiomes, biomeChoices))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.reasons")));
		for (EntitySpawnReason reason : REASONS) {
			String name = reason.name();
			rows.add(RowList.row(reasonName(reason), null, Component.translatable("controlmobspawns.reason." + name.toLowerCase(Locale.ROOT) + ".hint"),
				Widgets.toggle(!settings.disabledReasons.contains(name), 80,
					Component.translatable("controlmobspawns.gui.enabled"), Component.translatable("controlmobspawns.gui.disabled"),
					enabled -> {
						settings.disabledReasons.remove(name);
						if (!enabled) {
							settings.disabledReasons.add(name);
						}
					})));
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.nbt")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_bans.title"), null,
			Component.translatable("controlmobspawns.gui.nbt_bans.hint"),
			editButton(settings.nbtBans.size(), () -> new NbtBansScreen(this, session, id, settings))));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_proportions.title"), null,
			Component.translatable("controlmobspawns.gui.nbt_proportions.hint"),
			editButton(settings.nbtProportions.size(), () -> new NbtProportionsScreen(this, session, id, settings))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.rules")));
		Component ruleState = Component.translatable(settings.spawnRule != null && settings.spawnRule.enabled
			? "controlmobspawns.gui.rule.active" : "controlmobspawns.gui.rule.none");
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.title"), null,
			Component.translatable("controlmobspawns.gui.rule.hint"),
			Widgets.button(ruleState, 100, button -> minecraft.gui.setScreen(new SpawnRuleScreen(this, session, settings)))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.despawn_mode"), null,
			Component.translatable("controlmobspawns.gui.despawn_mode.hint"),
			Widgets.cycle(List.of(CmsConfig.DespawnMode.values()), settings.despawn, 100,
				mode -> Component.translatable("controlmobspawns.despawn_mode." + mode.name().toLowerCase(Locale.ROOT)),
				mode -> settings.despawn = mode)));

		if (session.remote) {
			rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn_now")));
			if (counts == null) {
				rows.add(RowList.row(Component.translatable("controlmobspawns.gui.loading").withStyle(ChatFormatting.GRAY)));
			} else {
				int total = counts.values().stream().mapToInt(Integer::intValue).sum();
				rows.add(despawnRow(Component.translatable("controlmobspawns.gui.current.all_dimensions"), "", total));
				counts.forEach((dimension, count) -> rows.add(despawnRow(Component.literal(dimension), dimension, count)));
			}
		}
		return rows;
	}

	private RowList.Row despawnRow(Component where, String dimension, int count) {
		return RowList.row(Component.translatable("controlmobspawns.gui.mob.despawn_now.row", where, count), null,
			Component.translatable("controlmobspawns.gui.current.despawn_all.hint"),
			Widgets.button(Component.translatable("controlmobspawns.gui.current.despawn_all"), 100,
				button -> ClientPlayNetworking.send(new CmsPayloads.DespawnAll(id, dimension))));
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

	private Button editButton(int count, Supplier<Screen> screen) {
		return Widgets.button(Component.translatable("controlmobspawns.gui.edit_count", count), 100, button -> minecraft.gui.setScreen(screen.get()));
	}

	public static Component reasonName(EntitySpawnReason reason) {
		return Component.translatable("controlmobspawns.reason." + reason.name().toLowerCase(Locale.ROOT));
	}
}
