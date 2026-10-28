package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Adds mobs to the natural spawn lists of biomes. */
public class BiomeSpawnsScreen extends CmsScreen {
	public BiomeSpawnsScreen(Screen parent, ClientSession session) {
		super(parent, session, Component.translatable("controlmobspawns.gui.biome_spawns.title"));
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.help").withStyle(ChatFormatting.GRAY), null,
			Component.translatable("controlmobspawns.gui.biome_spawns.hint")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.biome_spawns.columns").withStyle(ChatFormatting.GRAY)));
		List<String> biomeChoices = Stream.concat(session.biomes.stream(), session.biomeTags.stream().map(tag -> "#" + tag)).toList();
		for (CmsConfig.BiomeSpawn spawn : List.copyOf(session.config.biomeSpawns)) {
			EntityType<?> type = RuntimeConfig.entityType(spawn.entity);
			rows.add(RowList.row(Component.empty(), type == null ? null : Mobs.icon(type), null,
				Widgets.textBox(spawn.entity, 120, Component.translatable("controlmobspawns.gui.biome_spawns.entity"),
					BiomeSpawnsScreen::isSpawnableEntity, value -> spawn.entity = value),
				Widgets.button(Component.translatable("controlmobspawns.gui.biome_spawns.biomes", spawn.biomes.size()), 80,
					button -> minecraft.gui.setScreen(new IdListScreen(this, session,
						Component.translatable("controlmobspawns.gui.biome_spawns.biomes_title"), spawn.biomes, biomeChoices))),
				Widgets.intBox(spawn.weight, 40, Component.translatable("controlmobspawns.gui.biome_spawns.weight"), 1, value -> spawn.weight = value),
				Widgets.intBox(spawn.minCount, 30, Component.translatable("controlmobspawns.gui.min"), 1, value -> spawn.minCount = value),
				Widgets.intBox(spawn.maxCount, 30, Component.translatable("controlmobspawns.gui.max"), 1, value -> spawn.maxCount = value),
				Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					session.config.biomeSpawns.remove(spawn);
					refresh();
				})));
		}
		return rows;
	}

	private static boolean isSpawnableEntity(String id) {
		EntityType<?> type = RuntimeConfig.entityType(id);
		return type != null && type.getCategory() != MobCategory.MISC;
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.add"), 100, button -> {
			session.config.biomeSpawns.add(new CmsConfig.BiomeSpawn());
			refresh();
		}));
	}

	@Override
	protected void onDone() {
		session.save();
	}
}
