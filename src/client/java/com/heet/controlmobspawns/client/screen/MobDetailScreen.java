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

	/** The mob, for its live counts; for a group or a selection, its first mob. */
	private final String id;
	/** The mobs whose NBT paths are suggested: the mob, the group's members or the selection. */
	private final List<String> nbtEntities;
	private final CmsConfig.MobSettings settings;
	/** Whether this edits one mob: only then are inherited values and live counts shown. */
	private final boolean singleMob;
	private final Supplier<List<MobSettingsResolver.Source>> inheritedFrom;
	private final @Nullable Runnable onDone;
	private @Nullable Map<String, Integer> counts;

	public MobDetailScreen(Screen parent, ClientSession session, EntityType<?> type) {
		this(parent, session, Component.translatable("controlmobspawns.gui.mob.title", Mobs.name(type)), List.of(Mobs.id(type)),
			session.config.getOrCreateMob(Mobs.id(type)), true, () -> MobSettingsResolver.groupSources(session.config, type), null);
	}

	private MobDetailScreen(Screen parent, ClientSession session, Component title, List<String> mobs, CmsConfig.MobSettings settings, boolean singleMob,
		Supplier<List<MobSettingsResolver.Source>> inheritedFrom, @Nullable Runnable onDone) {
		super(parent, session, title);
		this.id = mobs.isEmpty() ? "" : mobs.getFirst();
		this.nbtEntities = mobs;
		this.settings = settings;
		this.singleMob = singleMob;
		this.inheritedFrom = inheritedFrom;
		this.onDone = onDone;
	}

	/** Edits the settings a group gives to its members. */
	public static MobDetailScreen forGroup(Screen parent, ClientSession session, CmsConfig.MobGroup group) {
		List<String> members = Mobs.all().stream().filter(type -> MobSettingsResolver.isMember(group, type)).map(Mobs::id).toList();
		return new MobDetailScreen(parent, session, Component.translatable("controlmobspawns.gui.group.title", MobSettingsResolver.groupName(group)),
			members, group.settings, false, List::of, null);
	}

	/**
	 * Edits several mobs at once: the screen starts from vanilla values, and what is changed is
	 * written into each mob when the screen closes.
	 */
	public static MobDetailScreen forSelection(Screen parent, ClientSession session, List<String> ids, Runnable afterApply) {
		CmsConfig.MobSettings scratch = new CmsConfig.MobSettings();
		return new MobDetailScreen(parent, session, Component.translatable("controlmobspawns.gui.selection.title", ids.size()), ids,
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
		Inheritance from = inheritance();
		if (singleMob) {
			groupsRow(rows, from);
		}
		CmsConfig.MobSettings effective = from.effective();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.spawn")));
		rows.add(reset(RowList.row(Component.translatable("controlmobspawns.gui.spawn_rate"))
			.info(Component.translatable("controlmobspawns.gui.spawn_rate.hint"))
			.rest(value(Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(settings.spawnRate)), settings.spawnRate != 100.0))
			.badge(from.scalar(MobSettingsResolver.SPAWN_RATE, settings.spawnRate != 100.0,
				Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(effective.spawnRate))))
			.hover(Widgets.positiveDoubleBox(settings.spawnRate, 60, Component.translatable("controlmobspawns.gui.spawn_rate"), value -> settings.spawnRate = value)),
			settings.spawnRate != 100.0, () -> settings.spawnRate = 100.0));
		rows.add(reset(RowList.row(Component.translatable("controlmobspawns.gui.mob.global_ban"))
			.rest(banState(settings.banned))
			.badge(from.scalar(MobSettingsResolver.BANNED, settings.banned, banState(effective.banned)))
			.hover(Widgets.toggle(!settings.banned, 80, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					settings.banned = !allowed;
					refresh();
				})), settings.banned, () -> settings.banned = false));
		rows.add(reset(listRow(Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), null, settings.bannedDimensions.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_dimensions"), settings.bannedDimensions, session.dimensions))
			.badge(from.list(MobSettingsResolver.BANNED_DIMENSIONS)),
			!settings.bannedDimensions.isEmpty(), settings.bannedDimensions::clear));
		List<String> biomeChoices = Stream.concat(session.biomes.stream(), session.biomeTags.stream().map(tag -> "#" + tag)).toList();
		rows.add(reset(listRow(Component.translatable("controlmobspawns.gui.mob.banned_biomes"), Component.translatable("controlmobspawns.gui.ids.hint"),
			settings.bannedBiomes.size(),
			() -> new IdListScreen(this, session, Component.translatable("controlmobspawns.gui.mob.banned_biomes"), settings.bannedBiomes, biomeChoices))
			.badge(from.list(MobSettingsResolver.BANNED_BIOMES)),
			!settings.bannedBiomes.isEmpty(), settings.bannedBiomes::clear));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.rules")));
		boolean ruleActive = effective.spawnRule != null && effective.spawnRule.enabled;
		boolean ownRule = settings.spawnRule != null && settings.spawnRule.enabled;
		rows.add(reset(RowList.row(Component.translatable("controlmobspawns.gui.rule.title"))
			.info(Component.translatable("controlmobspawns.gui.rule.hint"))
			.rest(value(Component.translatable(ownRule ? "controlmobspawns.gui.rule.active" : "controlmobspawns.gui.rule.none"), ownRule))
			.badge(from.scalar(MobSettingsResolver.SPAWN_RULE, ownRule, Component.translatable(ruleActive ? "controlmobspawns.gui.rule.active" : "controlmobspawns.gui.rule.none")))
			.hover(Widgets.button(Component.translatable("controlmobspawns.gui.edit"), 80,
				button -> minecraft.gui.setScreen(new SpawnRuleScreen(this, session, settings)))),
			settings.spawnRule != null, () -> settings.spawnRule = null));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.despawn")));
		rows.add(reset(RowList.row(Component.translatable("controlmobspawns.gui.despawn_mode"))
			.info(Component.translatable("controlmobspawns.gui.despawn_mode.hint"))
			.rest(value(despawnModeName(settings.despawn), settings.despawn != CmsConfig.DespawnMode.DEFAULT))
			.badge(from.scalar(MobSettingsResolver.DESPAWN, settings.despawn != CmsConfig.DespawnMode.DEFAULT, despawnModeName(effective.despawn)))
			.hover(Widgets.cycle(List.of(CmsConfig.DespawnMode.values()), settings.despawn, 100, MobDetailScreen::despawnModeName,
				mode -> {
					settings.despawn = mode;
					refresh();
				})), settings.despawn != CmsConfig.DespawnMode.DEFAULT, () -> settings.despawn = CmsConfig.DespawnMode.DEFAULT));

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

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.nbt")));
		rows.add(reset(listRow(Component.translatable("controlmobspawns.gui.nbt_bans.title"), Component.translatable("controlmobspawns.gui.nbt_bans.hint"),
			settings.nbtBans.size(), () -> new NbtBansScreen(this, session, nbtEntities, settings))
			.badge(from.list(MobSettingsResolver.NBT_BANS)),
			!settings.nbtBans.isEmpty(), settings.nbtBans::clear));
		rows.add(reset(listRow(Component.translatable("controlmobspawns.gui.nbt_proportions.title"), Component.translatable("controlmobspawns.gui.nbt_proportions.hint"),
			settings.nbtProportions.size(), () -> new NbtProportionsScreen(this, session, nbtEntities, settings))
			.badge(from.list(MobSettingsResolver.NBT_PROPORTIONS)),
			!settings.nbtProportions.isEmpty(), settings.nbtProportions::clear));

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.mob.reasons")));
		for (EntitySpawnReason reason : REASONS) {
			String name = reason.name();
			boolean enabled = !settings.disabledReasons.contains(name);
			rows.add(reset(RowList.row(reasonName(reason))
				.info(Component.translatable("controlmobspawns.reason." + name.toLowerCase(Locale.ROOT) + ".hint"))
				.rest(enabled
					? Component.translatable("controlmobspawns.gui.enabled").withStyle(ChatFormatting.GRAY)
					: Component.translatable("controlmobspawns.gui.disabled").withStyle(ChatFormatting.RED))
				.badge(enabled ? from.entry(MobSettingsResolver.key(MobSettingsResolver.DISABLED_REASONS, name),
					Component.translatable("controlmobspawns.gui.disabled")) : null)
				.hover(Widgets.toggle(enabled, 80, Component.translatable("controlmobspawns.gui.enabled"), Component.translatable("controlmobspawns.gui.disabled"),
					value -> {
						settings.disabledReasons.remove(name);
						if (!value) {
							settings.disabledReasons.add(name);
						}
						refresh();
					})), !enabled, () -> settings.disabledReasons.remove(name)));
		}
		return rows;
	}

	/**
	 * What the mob's groups give it: the values that apply and, for each setting, the groups it
	 * comes from. Groups give nothing to a group, a selection or a mob that ignores them.
	 */
	private Inheritance inheritance() {
		List<MobSettingsResolver.Source> groups = singleMob && !settings.ignoreGroups ? inheritedFrom.get() : List.of();
		if (groups.isEmpty()) {
			return new Inheritance(settings, List.of());
		}
		List<MobSettingsResolver.Source> levels = new ArrayList<>();
		levels.add(new MobSettingsResolver.Source("", settings));
		levels.addAll(groups);
		return new Inheritance(MobSettingsResolver.merge(levels), MobSettingsResolver.inherited(groups));
	}

	private record Inheritance(CmsConfig.MobSettings effective, List<MobSettingsResolver.Inherited> inherited) {
		/**
		 * "300 % - Undead": the value that applies and the groups giving it, unless the mob sets
		 * it itself, since its own value wins.
		 */
		@Nullable Component scalar(String key, boolean ownChanged, Component applied) {
			return ownChanged ? null : withValue(applied, names(entry -> entry.key().equals(key)));
		}

		/** The groups adding entries to a list; they add up with the mob's own entries. */
		@Nullable Component list(String kind) {
			return names(entry -> entry.key().startsWith(kind + ":"));
		}

		@Nullable Component entry(String key, Component applied) {
			return withValue(applied, names(entry -> entry.key().equals(key)));
		}

		private static @Nullable Component withValue(Component applied, @Nullable Component names) {
			return names == null ? null : Component.empty().append(applied.copy().withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(names);
		}

		/** "Undead, Raiders" in aqua, or null when no group gives the value. */
		private @Nullable Component names(java.util.function.Predicate<MobSettingsResolver.Inherited> filter) {
			List<String> sources = inherited.stream().filter(filter).map(MobSettingsResolver.Inherited::source).distinct().toList();
			if (sources.isEmpty()) {
				return null;
			}
			return Component.literal(String.join(", ", sources)).withStyle(ChatFormatting.AQUA);
		}
	}

	/** The switch to ignore the mob's groups: then every setting of the mob applies, vanilla ones included. */
	private void groupsRow(List<RowList.Row> rows, Inheritance from) {
		if (inheritedFrom.get().isEmpty() && !settings.ignoreGroups) {
			return;
		}
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.inherited")).info(Component.translatable("controlmobspawns.gui.inherited.hint")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.inherited.ignore"))
			.info(Component.translatable("controlmobspawns.gui.inherited.ignore.hint"))
			.add(Widgets.toggle(!settings.ignoreGroups, 80, Component.translatable("controlmobspawns.gui.inherited.applied"),
				Component.translatable("controlmobspawns.gui.inherited.ignored"), applied -> {
					settings.ignoreGroups = !applied;
					refresh();
				})));
	}

	/** Adds the reset button putting a value back to vanilla, while it is changed; the rest of the row does not move. */
	private RowList.Row reset(RowList.Row row, boolean modified, Runnable toVanilla) {
		if (modified) {
			row.fixed(Widgets.resetButton(Component.translatable("controlmobspawns.gui.reset_vanilla"), () -> {
				toVanilla.run();
				refresh();
			}));
		}
		return row;
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
