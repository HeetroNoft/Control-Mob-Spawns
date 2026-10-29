package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.MobSettingsResolver;
import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The ⚙ sub-menu of a mob, a group or several selected mobs: each value as text, its control on
 * hover.
 */
public class MobDetailScreen extends CmsScreen {
	/** Reasons that are not new spawns and cannot be disabled. */
	private static final List<EntitySpawnReason> REASONS = Arrays.stream(EntitySpawnReason.values())
		.filter(reason -> reason != EntitySpawnReason.LOAD && reason != EntitySpawnReason.DIMENSION_TRAVEL)
		.toList();
	/** Settings copied with "Copy", shared by every screen until the game closes. */
	private static CmsConfig.@Nullable MobSettings clipboard;

	/** The mob whose NBT paths are suggested; for a group, one of its members. */
	private final String id;
	private final CmsConfig.MobSettings settings;
	/** Whether this edits one mob: only then are inherited values and live counts shown. */
	private final boolean singleMob;
	private final Supplier<List<MobSettingsResolver.Source>> inheritedFrom;
	private final @Nullable Runnable onDone;
	private @Nullable Map<String, Integer> counts;

	public MobDetailScreen(Screen parent, ClientSession session, EntityType<?> type) {
		this(parent, session, Component.translatable("controlmobspawns.gui.mob.title", Mobs.name(type)), Mobs.id(type),
			session.config.getOrCreateMob(Mobs.id(type)), true, () -> MobSettingsResolver.groupSources(session.config, type), null);
	}

	private MobDetailScreen(Screen parent, ClientSession session, Component title, String id, CmsConfig.MobSettings settings, boolean singleMob,
		Supplier<List<MobSettingsResolver.Source>> inheritedFrom, @Nullable Runnable onDone) {
		super(parent, session, title);
		this.id = id;
		this.settings = settings;
		this.singleMob = singleMob;
		this.inheritedFrom = inheritedFrom;
		this.onDone = onDone;
	}

	/** Edits the settings a group gives to its members. */
	public static MobDetailScreen forGroup(Screen parent, ClientSession session, CmsConfig.MobGroup group) {
		String member = Mobs.all().stream().filter(type -> MobSettingsResolver.isMember(group, type)).findFirst().map(Mobs::id).orElse("");
		return new MobDetailScreen(parent, session, Component.translatable("controlmobspawns.gui.group.title", MobSettingsResolver.groupName(group)),
			member, group.settings, false, List::of, null);
	}

	/**
	 * Edits several mobs at once: the screen starts from vanilla values, and what is changed is
	 * written into each mob when the screen closes.
	 */
	public static MobDetailScreen forSelection(Screen parent, ClientSession session, List<String> ids, Runnable afterApply) {
		CmsConfig.MobSettings scratch = new CmsConfig.MobSettings();
		return new MobDetailScreen(parent, session, Component.translatable("controlmobspawns.gui.selection.title", ids.size()), ids.getFirst(),
			scratch, false, List::of, () -> {
				for (String mob : ids) {
					scratch.applyChangesTo(session.config.getOrCreateMob(mob));
				}
				afterApply.run();
			});
	}

	public static boolean hasClipboard() {
		return clipboard != null;
	}

	/** Replaces these settings with the copied ones. */
	public static void pasteInto(CmsConfig.MobSettings target) {
		if (clipboard != null) {
			target.setFrom(clipboard);
		}
	}

	@Override
	protected @Nullable Component help() {
		return onDone != null ? Component.translatable("controlmobspawns.gui.selection.hint") : null;
	}

	@Override
	protected void onDone() {
		if (onDone != null) {
			onDone.run();
		}
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.copy"), 70, Component.translatable("controlmobspawns.gui.copy.hint"),
			button -> clipboard = settings.copy()));
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.paste"), 70, Component.translatable("controlmobspawns.gui.paste.hint"),
			button -> {
				pasteInto(settings);
				refresh();
			}));
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		if (singleMob) {
			inheritedRows(rows);
		}
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.spawn")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.spawn_rate"))
			.info(Component.translatable("controlmobspawns.gui.spawn_rate.hint"))
			.rest(value(Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(settings.spawnRate)), settings.spawnRate != 100.0))
			.hover(Widgets.positiveDoubleBox(settings.spawnRate, 60, Component.translatable("controlmobspawns.gui.spawn_rate"), value -> settings.spawnRate = value)));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.mob.global_ban"))
			.rest(banState(settings.banned))
			.hover(Widgets.toggle(!settings.banned, 80, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					settings.banned = !allowed;
					refresh();
				})));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), null, settings.bannedDimensions.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), settings.bannedDimensions, session.dimensions)));
		List<String> biomeChoices = Stream.concat(session.biomes.stream(), session.biomeTags.stream().map(tag -> "#" + tag)).toList();
		rows.add(listRow(Component.translatable("controlmobspawns.gui.mob.banned_biomes"), Component.translatable("controlmobspawns.gui.ids.hint"),
			settings.bannedBiomes.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_biomes"), settings.bannedBiomes, biomeChoices)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.reasons")));
		for (EntitySpawnReason reason : REASONS) {
			String name = reason.name();
			boolean enabled = !settings.disabledReasons.contains(name);
			rows.add(RowList.row(reasonName(reason))
				.info(Component.translatable("controlmobspawns.reason." + name.toLowerCase(Locale.ROOT) + ".hint"))
				.rest(enabled
					? Component.translatable("controlmobspawns.gui.enabled").withStyle(ChatFormatting.GRAY)
					: Component.translatable("controlmobspawns.gui.disabled").withStyle(ChatFormatting.RED))
				.hover(Widgets.toggle(enabled, 80, Component.translatable("controlmobspawns.gui.enabled"), Component.translatable("controlmobspawns.gui.disabled"),
					value -> {
						settings.disabledReasons.remove(name);
						if (!value) {
							settings.disabledReasons.add(name);
						}
						refresh();
					})));
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.nbt")));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.nbt_bans.title"), Component.translatable("controlmobspawns.gui.nbt_bans.hint"),
			settings.nbtBans.size(), () -> new NbtBansScreen(this, session, id, settings)));
		rows.add(listRow(Component.translatable("controlmobspawns.gui.nbt_proportions.title"), Component.translatable("controlmobspawns.gui.nbt_proportions.hint"),
			settings.nbtProportions.size(), () -> new NbtProportionsScreen(this, session, id, settings)));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.rules")));
		boolean ruleActive = settings.spawnRule != null && settings.spawnRule.enabled;
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.rule.title"))
			.info(Component.translatable("controlmobspawns.gui.rule.hint"))
			.rest(value(Component.translatable(ruleActive ? "controlmobspawns.gui.rule.active" : "controlmobspawns.gui.rule.none"), ruleActive))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.edit"), 80,
				button -> minecraft.gui.setScreen(new SpawnRuleScreen(this, session, settings)))));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.despawn_mode"))
			.info(Component.translatable("controlmobspawns.gui.despawn_mode.hint"))
			.rest(value(despawnModeName(settings.despawn), settings.despawn != CmsConfig.DespawnMode.DEFAULT))
			.hover(Widgets.cycle(List.of(CmsConfig.DespawnMode.values()), settings.despawn, 100, MobDetailScreen::despawnModeName,
				mode -> {
					settings.despawn = mode;
					refresh();
				})));

		if (session.remote && singleMob) {
			rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn_now"))
				.info(Component.translatable("controlmobspawns.gui.mob.despawn_now.hint")));
			if (counts == null) {
				rows.add(RowList.note(Component.translatable("controlmobspawns.gui.loading")));
			} else {
				int total = counts.values().stream().mapToInt(Integer::intValue).sum();
				rows.add(despawnRow(Component.translatable("controlmobspawns.gui.current.all_dimensions"), "", total));
				counts.forEach((dimension, count) -> rows.add(despawnRow(Component.literal(dimension), dimension, count)));
			}
		}
		return rows;
	}

	/**
	 * What the mob's groups and tags give it, each with a switch to put it back to vanilla for this
	 * mob. Forced values that are no longer inherited can be removed.
	 */
	private void inheritedRows(List<RowList.Row> rows) {
		List<MobSettingsResolver.Inherited> inherited = MobSettingsResolver.inherited(inheritedFrom.get());
		List<String> stale = settings.forceVanilla.stream().filter(key -> inherited.stream().noneMatch(entry -> entry.key().equals(key))).toList();
		if (inherited.isEmpty() && stale.isEmpty()) {
			return;
		}
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.inherited")).info(Component.translatable("controlmobspawns.gui.inherited.hint")));
		for (MobSettingsResolver.Inherited entry : inherited) {
			boolean forced = settings.forceVanilla.contains(entry.key());
			Component label = Component.translatable("controlmobspawns.gui.inherited.entry", describe(entry.key(), entry.value()),
				Component.literal(entry.source()).withStyle(ChatFormatting.AQUA));
			rows.add(RowList.row(forced ? label.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH) : label)
				.hover(Widgets.toggle(!forced, 80, Component.translatable("controlmobspawns.gui.inherited.applied"),
					Component.translatable("controlmobspawns.gui.inherited.vanilla"), applied -> {
						settings.forceVanilla.remove(entry.key());
						if (!applied) {
							settings.forceVanilla.add(entry.key());
						}
						refresh();
					})));
		}
		for (String key : stale) {
			int colon = key.indexOf(':');
			rows.add(RowList.row(Component.translatable("controlmobspawns.gui.inherited.stale", describe(key, colon < 0 ? "" : key.substring(colon + 1)))
					.withStyle(ChatFormatting.GRAY))
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					settings.forceVanilla.remove(key);
					refresh();
				})));
		}
	}

	/** "Banned biomes: #minecraft:is_ocean", from a force-vanilla key and its value. */
	private static Component describe(String key, String value) {
		int colon = key.indexOf(':');
		String kind = colon < 0 ? key : key.substring(0, colon);
		Component name = Component.translatable(switch (kind) {
			case MobSettingsResolver.SPAWN_RATE -> "controlmobspawns.gui.spawn_rate";
			case MobSettingsResolver.BANNED -> "controlmobspawns.gui.mob.global_ban";
			case MobSettingsResolver.DESPAWN -> "controlmobspawns.gui.despawn_mode";
			case MobSettingsResolver.SPAWN_RULE -> "controlmobspawns.gui.rule.title";
			case MobSettingsResolver.BANNED_DIMENSIONS -> "controlmobspawns.gui.mob.banned_dimensions";
			case MobSettingsResolver.BANNED_BIOMES -> "controlmobspawns.gui.mob.banned_biomes";
			case MobSettingsResolver.DISABLED_REASONS -> "controlmobspawns.gui.mob.reasons";
			case MobSettingsResolver.NBT_BANS -> "controlmobspawns.gui.nbt_bans.title";
			case MobSettingsResolver.NBT_PROPORTIONS -> "controlmobspawns.gui.nbt_proportions.title";
			default -> kind;
		});
		Component shown = switch (kind) {
			case MobSettingsResolver.BANNED, MobSettingsResolver.SPAWN_RULE -> null;
			case MobSettingsResolver.DESPAWN -> enumValue(CmsConfig.DespawnMode.class, value).map(MobDetailScreen::despawnModeName).orElse(Component.literal(value));
			case MobSettingsResolver.DISABLED_REASONS -> enumValue(EntitySpawnReason.class, value).map(MobDetailScreen::reasonName).orElse(Component.literal(value));
			default -> value.isEmpty() ? null : Component.literal(value);
		};
		return shown == null ? name : Component.translatable("controlmobspawns.gui.inherited.value", name, shown);
	}

	private static <E extends Enum<E>> Optional<E> enumValue(Class<E> type, String name) {
		try {
			return Optional.of(Enum.valueOf(type, name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** A row opening a list editor; the button shows the number of entries. */
	private RowList.Row listRow(Component label, @Nullable Component info, int count, Supplier<Screen> screen) {
		return RowList.row(label).info(info)
			.hover(Widgets.button(Widgets.editLabel(count), 80, button -> minecraft.gui.setScreen(screen.get())));
	}

	private RowList.Row despawnRow(Component where, String dimension, int count) {
		return RowList.row(where)
			.rest(Component.literal(Integer.toString(count)).withStyle(ChatFormatting.GRAY))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.current.despawn_all"), 80,
				button -> ClientPlayNetworking.send(new CmsPayloads.DespawnAll(id, dimension))));
	}

	/** A value at rest: gray when vanilla, yellow when changed. */
	static Component value(Component text, boolean modified) {
		return text.copy().withStyle(modified ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
	}

	private static Component banState(boolean banned) {
		return banned
			? Component.translatable("controlmobspawns.gui.banned").withStyle(ChatFormatting.RED)
			: Component.translatable("controlmobspawns.gui.allowed").withStyle(ChatFormatting.GRAY);
	}

	private static Component despawnModeName(CmsConfig.DespawnMode mode) {
		return Component.translatable("controlmobspawns.despawn_mode." + mode.name().toLowerCase(Locale.ROOT));
	}

	/** Counts of this mob per dimension; the server sends them again after each despawn. */
	@Override
	public void added() {
		super.added();
		if (session.remote && singleMob) {
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

	public static Component reasonName(EntitySpawnReason reason) {
		return Component.translatable("controlmobspawns.reason." + reason.name().toLowerCase(Locale.ROOT));
	}
}
