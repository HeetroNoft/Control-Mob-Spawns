package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Adds mobs to the natural spawns of biomes. */
class BiomeSpawnsTab implements MainConfigScreen.Tab {
	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.gui.biome_spawns.title");
	}

	@Override
	public LayoutElement toolbar(MainConfigScreen screen, int rowWidth) {
		return screen.toolbarLine(rowWidth, Component.translatable("controlmobspawns.gui.biome_spawns.columns").withStyle(ChatFormatting.GRAY),
			Widgets.info(Component.translatable("controlmobspawns.gui.biome_spawns.hint")),
			Widgets.button(Component.translatable("controlmobspawns.gui.add"), 70, button -> {
				screen.session().config.biomeSpawns.add(new CmsConfig.BiomeSpawn());
				screen.refresh();
			}));
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		List<RowList.Row> rows = new ArrayList<>();
		List<CmsConfig.BiomeSpawn> spawns = screen.session().config.biomeSpawns;
		if (spawns.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.biome_spawns.empty")));
		}
		List<String> biomeChoices = Stream.concat(screen.session().biomes.stream(), screen.session().biomeTags.stream().map(tag -> "#" + tag)).toList();
		List<String> entityIds = Mobs.all().stream().filter(type -> type.getCategory() != MobCategory.MISC).map(Mobs::id).toList();
		for (CmsConfig.BiomeSpawn spawn : List.copyOf(spawns)) {
			if (!Mobs.contains(spawn.entity, search)) {
				continue;
			}
			EntityType<?> type = RuntimeConfig.entityType(spawn.entity);
			EditBox entity = screen.autocomplete(Widgets.textBox(spawn.entity, 130, Component.translatable("controlmobspawns.gui.biome_spawns.entity"),
				BiomeSpawnsTab::isSpawnableEntity, value -> spawn.entity = value), () -> entityIds);
			entity.setHint(Component.translatable("controlmobspawns.gui.biome_spawns.entity").withStyle(ChatFormatting.DARK_GRAY));
			rows.add(RowList.row(Component.empty())
				.icon(type == null ? null : Mobs.icon(type))
				.add(entity)
				.add(Widgets.button(Component.translatable("controlmobspawns.gui.biome_spawns.biomes", spawn.biomes.size()), 80,
					button -> screen.open(new IdListScreen(screen, screen.session(),
						Component.translatable("controlmobspawns.gui.biome_spawns.biomes_title"), spawn.biomes, biomeChoices))))
				.add(Widgets.intBox(spawn.weight, 40, Component.translatable("controlmobspawns.gui.biome_spawns.weight"), 1, value -> spawn.weight = value))
				.add(Widgets.intBox(spawn.minCount, 30, Component.translatable("controlmobspawns.gui.min"), 1, value -> spawn.minCount = value))
				.add(Widgets.intBox(spawn.maxCount, 30, Component.translatable("controlmobspawns.gui.max"), 1, value -> spawn.maxCount = value))
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					spawns.remove(spawn);
					screen.refresh();
				}))
				// With the Biomes button the row has two actions, so the ✕ stays a hover action.
				.keepHover());
		}
		return rows;
	}

	private static boolean isSpawnableEntity(String id) {
		EntityType<?> type = RuntimeConfig.entityType(id);
		return type != null && type.getCategory() != MobCategory.MISC;
	}
}
