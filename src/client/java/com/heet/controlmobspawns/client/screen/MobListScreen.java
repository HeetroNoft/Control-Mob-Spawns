package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** Every mob with its spawn rate, a ban toggle and a ⚙ button opening its detailed settings. */
public class MobListScreen extends CmsScreen {
	public MobListScreen(Screen parent, ClientSession session) {
		super(parent, session, Component.translatable("controlmobspawns.gui.mobs.title"));
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mobs.columns").withStyle(ChatFormatting.GRAY)));
		for (EntityType<?> type : Mobs.all()) {
			if (Mobs.matches(type, search())) {
				rows.add(mobRow(type));
			}
		}
		return rows;
	}

	private RowList.Row mobRow(EntityType<?> type) {
		String id = Mobs.id(type);
		CmsConfig.MobSettings existing = session.config.mobs.get(id);
		boolean configured = existing != null && !existing.isDefault();
		Component label = Mobs.name(type).copy().withStyle(configured ? ChatFormatting.AQUA : ChatFormatting.WHITE);
		double rate = existing == null ? 100.0 : existing.spawnRate;
		boolean banned = existing != null && existing.banned;
		return RowList.row(label, Mobs.icon(type), Component.literal(id).withStyle(ChatFormatting.GRAY),
			Widgets.positiveDoubleBox(rate, 50, Component.translatable("controlmobspawns.gui.spawn_rate"),
				value -> session.config.getOrCreateMob(id).spawnRate = value),
			Widgets.toggle(!banned, 70, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> session.config.getOrCreateMob(id).banned = !allowed),
			Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.mob.settings"),
				button -> minecraft.gui.setScreen(new MobDetailScreen(this, session, type))));
	}

	@Override
	protected void onDone() {
		session.save();
	}
}
