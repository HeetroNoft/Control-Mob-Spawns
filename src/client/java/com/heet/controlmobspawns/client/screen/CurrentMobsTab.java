package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** Mobs in the world, most frequent first; Despawn, ban and ⚙ appear on hover. */
class CurrentMobsTab implements MainConfigScreen.Tab {
	private @Nullable List<CmsPayloads.MobCount> counts;

	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.config.current");
	}

	/** Counts are asked each time the tab is shown, including when coming back from ⚙. */
	@Override
	public void onShow(MainConfigScreen screen) {
		ControlMobSpawnsClient.setMobCountListener(received -> {
			counts = received;
			screen.reinit();
		});
		ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts());
	}

	@Override
	public void onHide() {
		ControlMobSpawnsClient.setMobCountListener(null);
	}

	@Override
	public LayoutElement toolbar(MainConfigScreen screen, int rowWidth) {
		CmsConfig config = screen.session().config;
		Component total = counts == null
			? Component.translatable("controlmobspawns.gui.loading")
			: Component.translatable("controlmobspawns.config.current.total", counts.stream().mapToInt(CmsPayloads.MobCount::count).sum());
		return screen.toolbarLine(rowWidth, total,
			Widgets.checkbox(Component.translatable("controlmobspawns.config.current.despawn_current_dimension"),
				config.despawn.despawnButtonCurrentDimension, value -> config.despawn.despawnButtonCurrentDimension = value),
			Widgets.button(Component.literal("⟳"), 20, Component.translatable("controlmobspawns.gui.refresh"),
				button -> ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts())));
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		List<RowList.Row> rows = new ArrayList<>();
		if (counts == null) {
			return rows;
		}
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (CmsPayloads.MobCount count : counts) {
			totals.merge(count.entity(), count.count(), Integer::sum);
		}
		totals.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
			.forEach(entry -> {
				EntityType<?> type = RuntimeConfig.entityType(entry.getKey());
				if (type != null && Mobs.matches(type, search)) {
					rows.add(mobRow(screen, type, entry.getValue()));
				}
			});
		return rows;
	}

	private RowList.Row mobRow(MainConfigScreen screen, EntityType<?> type, int count) {
		String id = Mobs.id(type);
		CmsConfig config = screen.session().config;
		CmsConfig.MobSettings existing = config.mobs.get(id);
		boolean banned = existing != null && existing.banned;
		return RowList.row(Component.translatable("controlmobspawns.gui.current.row", Mobs.label(type, banned), count))
			.icon(Mobs.icon(type))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.current.despawn_all"), 70,
				Component.translatable("controlmobspawns.gui.current.despawn_all.hint"), button -> despawn(config, id)))
			.hover(Widgets.toggle(!banned, 70, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					config.getOrCreateMob(id).banned = !allowed;
					screen.refresh();
				}))
			.hover(Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.mob.settings"),
				button -> screen.open(new MobDetailScreen(screen, screen.session(), type))));
	}

	/** One-off removal, in every dimension or only the player's; the server answers with new counts. */
	private static void despawn(CmsConfig config, String id) {
		Minecraft minecraft = Minecraft.getInstance();
		String dimension = config.despawn.despawnButtonCurrentDimension && minecraft.level != null
			? minecraft.level.dimension().identifier().toString() : "";
		ClientPlayNetworking.send(new CmsPayloads.DespawnAll(id, dimension));
	}
}
