package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.heet.controlmobspawns.mixin.MobCategoryAccessor;
import com.heet.controlmobspawns.network.CmsPayloads;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

/** The YACL screen with the simple settings, and buttons leading to the custom screens. */
public final class ConfigScreens {
	private ConfigScreens() {
	}

	/** Opens the screen; in a world the mob counts are fetched first for the "Current mobs" tab. */
	public static void open(Screen parent, ClientSession session) {
		if (!session.remote) {
			Minecraft.getInstance().gui.setScreen(create(parent, session, null));
			return;
		}
		showWithNextCounts(parent, session, 0);
		ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts());
	}

	/** Builds and shows the screen on the given tab as soon as the server sends the mob counts. */
	private static void showWithNextCounts(Screen parent, ClientSession session, int tab) {
		ControlMobSpawnsClient.setMobCountListener(counts -> {
			ControlMobSpawnsClient.setMobCountListener(null);
			Screen screen = create(parent, session, counts);
			Minecraft.getInstance().gui.setScreen(screen);
			if (screen instanceof YACLScreen yacl && tab > 0) {
				yacl.tabNavigationBar.selectTab(tab, false);
			}
		});
	}

	/**
	 * Prepares a rebuild on the "Current mobs" tab: values changed but not saved yet are written
	 * into the session first, so the rebuilt screen keeps them.
	 */
	private static void rebuildOnNextCounts(YACLScreen screen, Screen parent, ClientSession session) {
		for (ConfigCategory category : screen.config.categories()) {
			for (OptionGroup group : category.groups()) {
				for (Option<?> option : group.options()) {
					if (option.changed()) {
						option.applyValue();
					}
				}
			}
		}
		showWithNextCounts(parent, session, screen.config.categories().size() - 1);
	}

	public static Screen create(Screen parent, ClientSession session, @Nullable List<CmsPayloads.MobCount> counts) {
		CmsConfig config = session.config;
		Component title = Component.translatable("controlmobspawns.title").append(" — ")
			.append(Component.translatable(session.scope == ClientSession.Scope.WORLD ? "controlmobspawns.gui.scope.world" : "controlmobspawns.gui.scope.global"));

		ConfigCategory.Builder general = ConfigCategory.createBuilder().name(Component.translatable("controlmobspawns.config.general"));
		OptionGroup.Builder screens = OptionGroup.createBuilder().name(Component.translatable("controlmobspawns.config.screens"))
			.option(openButton("mobs", screen -> new MobListScreen(screen, session)))
			.option(openButton("biome_spawns", screen -> new BiomeSpawnsScreen(screen, session)));
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

		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
			.title(title)
			.category(general.build())
			.category(caps.build())
			.category(despawn.build());
		if (counts != null) {
			builder.category(currentMobs(parent, session, counts));
		}
		return builder.save(session::save).build().generateScreen(parent);
	}

	/** One fixed row per mob present in the world, most frequent first, with its actions on hover. */
	private static ConfigCategory currentMobs(Screen parent, ClientSession session, List<CmsPayloads.MobCount> counts) {
		Map<String, Map<String, Integer>> perEntity = new LinkedHashMap<>();
		Map<String, Integer> totals = new HashMap<>();
		for (CmsPayloads.MobCount count : counts) {
			perEntity.computeIfAbsent(count.entity(), key -> new TreeMap<>()).merge(count.dimension(), count.count(), Integer::sum);
			totals.merge(count.entity(), count.count(), Integer::sum);
		}
		int total = totals.values().stream().mapToInt(Integer::intValue).sum();

		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(Component.translatable("controlmobspawns.config.current"))
			.tooltip(Component.translatable("controlmobspawns.config.current.desc"));
		category.option(ButtonOption.createBuilder()
			.name(Component.translatable("controlmobspawns.config.current.total", total))
			.description(OptionDescription.of(Component.translatable("controlmobspawns.config.current.refresh.desc")))
			.text(Component.translatable("controlmobspawns.gui.refresh"))
			.action(screen -> {
				rebuildOnNextCounts(screen, parent, session);
				ClientPlayNetworking.send(new CmsPayloads.RequestMobCounts());
			})
			.build());
		Option<Boolean> currentDimensionOnly = bool("current.despawn_current_dimension", false,
			() -> session.config.despawn.despawnButtonCurrentDimension, value -> session.config.despawn.despawnButtonCurrentDimension = value);
		category.option(currentDimensionOnly);

		perEntity.keySet().stream()
			.sorted(Comparator.comparing((String entity) -> totals.get(entity)).reversed())
			.forEach(entity -> {
				EntityType<?> type = RuntimeConfig.entityType(entity);
				if (type == null) {
					return;
				}
				MutableComponent description = Component.literal(entity).withStyle(ChatFormatting.GRAY);
				perEntity.get(entity).forEach((dimension, count) ->
					description.append("\n").append(Component.literal(dimension + " : " + count).withStyle(ChatFormatting.WHITE)));
				description.append("\n\n").append(Component.translatable("controlmobspawns.config.current.row.desc"));
				category.option(Option.<Boolean>createBuilder()
					.name(Component.translatable("controlmobspawns.gui.current.row", Mobs.name(type), totals.get(entity)))
					.description(OptionDescription.of(description))
					.binding(false, () -> isBanned(session, entity), value -> session.config.getOrCreateMob(entity).banned = value)
					.customController(option -> new MobRowController(option,
						screen -> despawn(screen, parent, session, entity, currentDimensionOnly.pendingValue()),
						screen -> Minecraft.getInstance().gui.setScreen(new MobDetailScreen(screen, session, type))))
					.build());
			});
		return category.build();
	}

	private static boolean isBanned(ClientSession session, String entity) {
		CmsConfig.MobSettings settings = session.config.mobs.get(entity);
		return settings != null && settings.banned;
	}

	/** One-off removal in every dimension, or in the player's; the server answers with the new counts. */
	private static void despawn(YACLScreen screen, Screen parent, ClientSession session, String entity, boolean currentDimensionOnly) {
		Minecraft minecraft = Minecraft.getInstance();
		String dimension = currentDimensionOnly && minecraft.level != null ? minecraft.level.dimension().identifier().toString() : "";
		rebuildOnNextCounts(screen, parent, session);
		ClientPlayNetworking.send(new CmsPayloads.DespawnAll(entity, dimension));
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
