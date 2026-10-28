package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** Every mob; at rest only what differs from vanilla, the controls on hover. */
class MobsTab implements MainConfigScreen.Tab {
	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.gui.mobs.title");
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		List<RowList.Row> rows = new ArrayList<>();
		for (EntityType<?> type : Mobs.all()) {
			if (Mobs.matches(type, search)) {
				rows.add(mobRow(screen, type));
			}
		}
		return rows;
	}

	private static RowList.Row mobRow(MainConfigScreen screen, EntityType<?> type) {
		String id = Mobs.id(type);
		CmsConfig config = screen.session().config;
		CmsConfig.MobSettings existing = config.mobs.get(id);
		double rate = existing == null ? 100.0 : existing.spawnRate;
		boolean banned = existing != null && existing.banned;
		return RowList.row(Mobs.label(type, banned))
			.icon(Mobs.icon(type))
			.rest(summary(existing))
			.hover(Widgets.positiveDoubleBox(rate, 45, Component.translatable("controlmobspawns.gui.spawn_rate"),
				value -> config.getOrCreateMob(id).spawnRate = value))
			.hover(Widgets.toggle(!banned, 70, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					config.getOrCreateMob(id).banned = !allowed;
					screen.refresh();
				}))
			.hover(Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.mob.settings"),
				button -> screen.open(new MobDetailScreen(screen, screen.session(), type))));
	}

	/** What differs from vanilla, besides the ban shown by the red name: "250 % · settings". */
	private static Component summary(CmsConfig.@Nullable MobSettings settings) {
		if (settings == null) {
			return Component.empty();
		}
		MutableComponent text = Component.empty().withStyle(ChatFormatting.GRAY);
		if (settings.spawnRate != 100.0) {
			text.append(Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(settings.spawnRate)));
		}
		if (settings.hasDetailedSettings()) {
			if (!text.getSiblings().isEmpty()) {
				text.append(" · ");
			}
			text.append(Component.translatable("controlmobspawns.gui.mobs.other_settings"));
		}
		return text;
	}
}
