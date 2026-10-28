package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.mixin.MobCategoryAccessor;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;

/** The YACL screen with the simple settings, and buttons leading to the custom screens. */
public final class ConfigScreens {
	private ConfigScreens() {
	}

	public static Screen create(Screen parent, ClientSession session) {
		CmsConfig config = session.config;
		Component title = Component.translatable("controlmobspawns.title").append(" — ")
			.append(Component.translatable(session.scope == ClientSession.Scope.WORLD ? "controlmobspawns.gui.scope.world" : "controlmobspawns.gui.scope.global"));

		ConfigCategory.Builder general = ConfigCategory.createBuilder().name(Component.translatable("controlmobspawns.config.general"));
		OptionGroup.Builder screens = OptionGroup.createBuilder().name(Component.translatable("controlmobspawns.config.screens"))
			.option(openButton("mobs", screen -> new MobListScreen(screen, session)))
			.option(openButton("biome_spawns", screen -> new BiomeSpawnsScreen(screen, session)));
		if (session.remote) {
			screens.option(openButton("current", screen -> new CurrentMobsScreen(screen, session)));
		}
		general.group(screens.build());
		general.group(OptionGroup.createBuilder().name(Component.translatable("controlmobspawns.config.coverage"))
			.option(bool("coverage.modded", true, () -> config.coverage.moddedMobs, value -> config.coverage.moddedMobs = value))
			.option(bool("coverage.eggs", false, () -> config.coverage.eggsAndCommands, value -> config.coverage.eggsAndCommands = value))
			.option(bool("coverage.existing", false, () -> config.coverage.removeExisting, value -> config.coverage.removeExisting = value))
			.build());
		general.group(OptionGroup.createBuilder().name(Component.translatable("controlmobspawns.config.despawn"))
			.option(bool("despawn.item_pickers", false, () -> config.despawn.itemPickersDespawn, value -> config.despawn.itemPickersDespawn = value))
			.option(bool("despawn.force_named", false, () -> config.despawn.forceIncludesNamed, value -> config.despawn.forceIncludesNamed = value))
			.build());
		general.group(OptionGroup.createBuilder().name(Component.translatable("controlmobspawns.config.server"))
			.option(Option.<Integer>createBuilder()
				.name(Component.translatable("controlmobspawns.config.permission_level"))
				.description(OptionDescription.of(Component.translatable("controlmobspawns.config.permission_level.desc")))
				.binding(2, () -> config.permissionLevel, value -> config.permissionLevel = value)
				.controller(option -> IntegerSliderControllerBuilder.create(option).range(0, 4).step(1))
				.build())
			.build());

		ConfigCategory.Builder caps = ConfigCategory.createBuilder().name(Component.translatable("controlmobspawns.config.caps"));
		ConfigCategory.Builder despawn = ConfigCategory.createBuilder().name(Component.translatable("controlmobspawns.config.despawn_distances"));
		for (MobCategory category : MobCategory.values()) {
			if (category == MobCategory.MISC) {
				continue;
			}
			String name = category.getName();
			MobCategoryAccessor vanilla = (MobCategoryAccessor) (Object) category;
			Component categoryName = categoryName(category);
			int vanillaCap = vanilla.cms$getVanillaMax();
			caps.option(intOption(categoryName, Component.translatable("controlmobspawns.config.caps.option", vanillaCap), vanillaCap,
				() -> config.mobCaps.getOrDefault(name, vanillaCap),
				value -> {
					if (value == vanillaCap) {
						config.mobCaps.remove(name);
					} else {
						config.mobCaps.put(name, value);
					}
				}));

			int vanillaInstant = vanilla.cms$getVanillaDespawnDistance();
			int vanillaRandom = vanilla.cms$getVanillaNoDespawnDistance();
			despawn.group(OptionGroup.createBuilder().name(categoryName)
				.option(intOption(Component.translatable("controlmobspawns.config.despawn.instant"),
					Component.translatable("controlmobspawns.config.despawn.instant.desc", vanillaInstant), vanillaInstant,
					() -> distance(config, name).instant == null ? vanillaInstant : distance(config, name).instant,
					value -> {
						distance(config, name).instant = value == vanillaInstant ? null : value;
						cleanDistance(config, name);
					}))
				.option(intOption(Component.translatable("controlmobspawns.config.despawn.random"),
					Component.translatable("controlmobspawns.config.despawn.random.desc", vanillaRandom), vanillaRandom,
					() -> distance(config, name).random == null ? vanillaRandom : distance(config, name).random,
					value -> {
						distance(config, name).random = value == vanillaRandom ? null : value;
						cleanDistance(config, name);
					}))
				.build());
		}

		return YetAnotherConfigLib.createBuilder()
			.title(title)
			.category(general.build())
			.category(caps.build())
			.category(despawn.build())
			.save(session::save)
			.build()
			.generateScreen(parent);
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

	public static Component categoryName(MobCategory category) {
		return Component.translatableWithFallback("controlmobspawns.mob_category." + category.getName(), category.getName());
	}

	private static ButtonOption openButton(String key, Function<Screen, Screen> screen) {
		return ButtonOption.createBuilder()
			.name(Component.translatable("controlmobspawns.config.open." + key))
			.description(OptionDescription.of(Component.translatable("controlmobspawns.config.open." + key + ".desc")))
			.text(Component.translatable("controlmobspawns.config.open"))
			.action(yaclScreen -> Minecraft.getInstance().gui.setScreen(screen.apply(yaclScreen)))
			.build();
	}

	private static Option<Boolean> bool(String key, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return Option.<Boolean>createBuilder()
			.name(Component.translatable("controlmobspawns.config." + key))
			.description(OptionDescription.of(Component.translatable("controlmobspawns.config." + key + ".desc")))
			.binding(defaultValue, getter, setter)
			.controller(TickBoxControllerBuilder::create)
			.build();
	}

	private static Option<Integer> intOption(Component name, Component description, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter) {
		return Option.<Integer>createBuilder()
			.name(name)
			.description(OptionDescription.of(description))
			.binding(defaultValue, getter, setter)
			.controller(option -> IntegerFieldControllerBuilder.create(option).min(0))
			.build();
	}
}
