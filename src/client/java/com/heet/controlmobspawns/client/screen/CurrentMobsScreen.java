package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** Mobs currently in the world, most frequent first, with shortcuts to adjust, ban or remove them. */
public class CurrentMobsScreen extends CmsScreen {
	private static final String ALL_DIMENSIONS = "";

	private List<CmsPayloads.MobCount> counts = List.of();
	private boolean loaded;
	private String dimension = ALL_DIMENSIONS;

	public CurrentMobsScreen(Screen parent, ClientSession session) {
		super(parent, session, Component.translatable("controlmobspawns.gui.current.title"));
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected void init() {
		requestCounts();
		super.init();
	}

	/** The listener is set while this screen is shown, including after coming back from a sub-screen. */
	@Override
	public void added() {
		super.added();
		ControlMobSpawnsClient.setMobCountListener(received -> {
			counts = received;
			loaded = true;
			refresh();
		});
	}

	@Override
	public void removed() {
		super.removed();
		ControlMobSpawnsClient.setMobCountListener(null);
	}

	private void requestCounts() {
		ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts());
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		if (!loaded) {
			rows.add(RowList.row(Component.translatable("controlmobspawns.gui.loading").withStyle(ChatFormatting.GRAY)));
			return rows;
		}
		Map<String, Integer> perEntity = new LinkedHashMap<>();
		for (CmsPayloads.MobCount count : counts) {
			if (dimension.equals(ALL_DIMENSIONS) || dimension.equals(count.dimension())) {
				perEntity.merge(count.entity(), count.count(), Integer::sum);
			}
		}
		int total = perEntity.values().stream().mapToInt(Integer::intValue).sum();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.current.total", total).withStyle(ChatFormatting.GRAY)));
		perEntity.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
			.forEach(entry -> {
				EntityType<?> type = RuntimeConfig.entityType(entry.getKey());
				if (type != null && Mobs.matches(type, search())) {
					rows.add(mobRow(type, entry.getValue()));
				}
			});
		return rows;
	}

	private RowList.Row mobRow(EntityType<?> type, int count) {
		String id = Mobs.id(type);
		CmsConfig.MobSettings existing = session.config.mobs.get(id);
		boolean banned = existing != null && existing.banned;
		Component label = Component.translatable("controlmobspawns.gui.current.row", Mobs.name(type), count);
		return RowList.row(label, Mobs.icon(type), Component.literal(id).withStyle(ChatFormatting.GRAY),
			Widgets.button(Component.translatable("controlmobspawns.gui.current.despawn_all"), 90,
				Component.translatable("controlmobspawns.gui.current.despawn_all.hint"),
				button -> ClientPlayNetworking.send(new CmsPayloads.DespawnAll(id, dimension))),
			Widgets.toggle(!banned, 70, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> session.config.getOrCreateMob(id).banned = !allowed),
			Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.mob.settings"),
				button -> minecraft.gui.setScreen(new MobDetailScreen(this, session, type))));
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		List<String> dimensions = new ArrayList<>();
		dimensions.add(ALL_DIMENSIONS);
		dimensions.addAll(session.dimensions);
		footer.addChild(Widgets.cycle(dimensions, dimension, 150,
			value -> value.equals(ALL_DIMENSIONS) ? Component.translatable("controlmobspawns.gui.current.all_dimensions") : Component.literal(value),
			value -> {
				dimension = value;
				refresh();
			}));
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.refresh"), 80, button -> requestCounts()));
	}

	@Override
	protected void onDone() {
		session.save();
	}
}
