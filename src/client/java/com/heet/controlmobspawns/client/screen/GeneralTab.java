package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.mixin.MobCategoryAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

/** Toggles, permission level, mob caps and despawn distances, with their controls always visible. */
class GeneralTab implements MainConfigScreen.Tab {
	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.config.general");
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		CmsConfig config = screen.session().config;
		Sections sections = new Sections(search);

		sections.header(Component.translatable("controlmobspawns.config.coverage"), null);
		sections.add(toggle("coverage.modded", () -> config.coverage.moddedMobs, value -> config.coverage.moddedMobs = value));
		sections.add(toggle("coverage.eggs", () -> config.coverage.eggsAndCommands, value -> config.coverage.eggsAndCommands = value));
		sections.add(toggle("coverage.existing", () -> config.coverage.removeExisting, value -> config.coverage.removeExisting = value));

		sections.header(Component.translatable("controlmobspawns.config.despawn"), null);
		sections.add(toggle("despawn.item_pickers", () -> config.despawn.itemPickersDespawn, value -> config.despawn.itemPickersDespawn = value));
		sections.add(toggle("despawn.force_named", () -> config.despawn.forceIncludesNamed, value -> config.despawn.forceIncludesNamed = value));

		sections.header(Component.translatable("controlmobspawns.config.server"), null);
		EditBox permission = Widgets.vanillaIntBox(config.permissionLevel, 2, 40, Component.translatable("controlmobspawns.config.permission_level"),
			value -> config.permissionLevel = value == null ? 2 : Math.min(4, value));
		sections.add(RowList.row(Component.translatable("controlmobspawns.config.permission_level"))
			.info(Component.translatable("controlmobspawns.config.permission_level.desc"))
			.add(permission).hover(Widgets.resetButton(permission, 2)).keepHover());

		sections.header(Component.translatable("controlmobspawns.config.caps"), Component.translatable("controlmobspawns.config.caps.desc"));
		for (MobCategory category : categories()) {
			String name = category.getName();
			int vanilla = ((MobCategoryAccessor) (Object) category).cms$getVanillaMax();
			EditBox box = Widgets.vanillaIntBox(config.mobCaps.getOrDefault(name, vanilla), vanilla, 50, categoryName(category), value -> {
				if (value == null) {
					config.mobCaps.remove(name);
				} else {
					config.mobCaps.put(name, value);
				}
			});
			sections.add(RowList.row(categoryName(category))
				.info(Component.translatable("controlmobspawns.config.caps.option", vanilla))
				.add(box).hover(Widgets.resetButton(box, vanilla)).keepHover());
		}

		sections.header(Component.translatable("controlmobspawns.config.despawn_distances"), Component.translatable("controlmobspawns.config.despawn_distances.desc"));
		for (MobCategory category : categories()) {
			String name = category.getName();
			MobCategoryAccessor vanilla = (MobCategoryAccessor) (Object) category;
			int vanillaInstant = vanilla.cms$getVanillaDespawnDistance();
			int vanillaRandom = vanilla.cms$getVanillaNoDespawnDistance();
			CmsConfig.DespawnDistance current = config.despawn.distances.get(name);
			EditBox instant = Widgets.vanillaIntBox(current != null && current.instant != null ? current.instant : vanillaInstant, vanillaInstant, 50,
				Component.translatable("controlmobspawns.config.despawn.instant"), value -> {
					distance(config, name).instant = value;
					cleanDistance(config, name);
				});
			EditBox random = Widgets.vanillaIntBox(current != null && current.random != null ? current.random : vanillaRandom, vanillaRandom, 50,
				Component.translatable("controlmobspawns.config.despawn.random"), value -> {
					distance(config, name).random = value;
					cleanDistance(config, name);
				});
			instant.setTooltip(Tooltip.create(Component.translatable("controlmobspawns.config.despawn.instant")));
			random.setTooltip(Tooltip.create(Component.translatable("controlmobspawns.config.despawn.random")));
			sections.add(RowList.row(categoryName(category))
				.info(Component.translatable("controlmobspawns.config.despawn_distances.option", vanillaInstant, vanillaRandom))
				.add(instant).add(random)
				.hover(resetBoth(instant, vanillaInstant, random, vanillaRandom))
				.keepHover());
		}
		return sections.rows;
	}

	private static IconButton resetBoth(EditBox first, int firstVanilla, EditBox second, int secondVanilla) {
		IconButton button = new IconButton(IconButton.RESET, Component.translatable("controlmobspawns.gui.reset_vanilla"), () -> {
			first.setValue(Integer.toString(firstVanilla));
			second.setValue(Integer.toString(secondVanilla));
		});
		button.setTooltip(Tooltip.create(Component.translatable("controlmobspawns.gui.reset_vanilla")));
		return button;
	}

	private static RowList.Row toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return RowList.row(Component.translatable("controlmobspawns.config." + key))
			.info(Component.translatable("controlmobspawns.config." + key + ".desc"))
			.add(Widgets.checkbox(getter.get(), setter));
	}

	private static List<MobCategory> categories() {
		List<MobCategory> categories = new ArrayList<>(List.of(MobCategory.values()));
		categories.remove(MobCategory.MISC);
		return categories;
	}

	static Component categoryName(MobCategory category) {
		return Component.translatableWithFallback("controlmobspawns.mob_category." + category.getName(), category.getName());
	}

	private static CmsConfig.DespawnDistance distance(CmsConfig config, String category) {
		return config.despawn.distances.computeIfAbsent(category, key -> new CmsConfig.DespawnDistance());
	}

	private static void cleanDistance(CmsConfig config, String category) {
		CmsConfig.DespawnDistance distance = config.despawn.distances.get(category);
		if (distance != null && distance.instant == null && distance.random == null) {
			config.despawn.distances.remove(category);
		}
	}

	/** Filters rows by the search text; a section header is kept only if one of its rows matches. */
	private static final class Sections {
		private final List<RowList.Row> rows = new ArrayList<>();
		private final String search;
		private RowList.@Nullable Row pendingHeader;
		private boolean headerMatches;

		Sections(String search) {
			this.search = search;
		}

		void header(Component title, @Nullable Component info) {
			pendingHeader = RowList.header(title).info(info);
			headerMatches = Mobs.contains(title.getString(), search);
		}

		void add(RowList.Row row) {
			if (!headerMatches && !Mobs.contains(row.label().getString(), search)) {
				return;
			}
			if (pendingHeader != null) {
				rows.add(pendingHeader);
				pendingHeader = null;
			}
			rows.add(row);
		}
	}
}
