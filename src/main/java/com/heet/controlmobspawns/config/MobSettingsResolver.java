package com.heet.controlmobspawns.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Combines a mob's own settings with those of its groups. The most specific level wins: the mob,
 * then the groups in their order. Simple values come from
 * the first level that changes them; list entries are merged. A mob with
 * {@link CmsConfig.MobSettings#ignoreGroups} only gets its own settings, vanilla ones included.
 */
public final class MobSettingsResolver {
	public static final String SPAWN_RATE = "spawnRate";
	public static final String BANNED = "banned";
	public static final String DESPAWN = "despawn";
	public static final String SPAWN_RULE = "spawnRule";
	public static final String BANNED_DIMENSIONS = "bannedDimensions";
	public static final String BANNED_BIOMES = "bannedBiomes";
	public static final String DISABLED_REASONS = "disabledReasons";
	public static final String NBT_BANS = "nbtBans";
	public static final String NBT_PROPORTIONS = "nbtProportions";

	private MobSettingsResolver() {
	}

	/** One level of settings; the name says where an inherited value comes from. */
	public record Source(String name, CmsConfig.MobSettings settings) {
	}

	/** A value given by a level: its key, its value as text and the level's name. */
	public record Inherited(String key, String value, String source) {
	}

	private record Entry(Inherited info, Consumer<CmsConfig.MobSettings> apply) {
	}

	public static String key(String list, String entry) {
		return list + ":" + entry;
	}

	public static final String EXCLUDE = "!";

	/** Whether a mob matches one of the group's entries and none of its '!' exclusions. */
	public static boolean isMember(CmsConfig.MobGroup group, EntityType<?> type) {
		boolean included = false;
		for (String member : group.members) {
			if (member.startsWith(EXCLUDE)) {
				if (matches(member.substring(EXCLUDE.length()), type)) {
					return false;
				}
			} else if (!included && matches(member, type)) {
				included = true;
			}
		}
		return included;
	}

	/** Whether a mob matches an entity id, an entity tag ("#...") or a mob category ("@..."). */
	public static boolean matches(String entry, EntityType<?> type) {
		if (entry.startsWith("@")) {
			return type.getCategory().getName().equals(entry.substring(1));
		}
		if (entry.startsWith("#")) {
			Identifier tag = Identifier.tryParse(entry.substring(1));
			return tag != null && hasTag(type, TagKey.create(Registries.ENTITY_TYPE, tag));
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(Identifier.tryParse(entry));
	}

	/** Tags are not bound before a world is loaded (title screen): no tag matches then. */
	private static boolean hasTag(EntityType<?> type, TagKey<EntityType<?>> tag) {
		try {
			return type.builtInRegistryHolder().is(tag);
		} catch (IllegalStateException e) {
			return false;
		}
	}

	public static String groupName(CmsConfig.MobGroup group) {
		return group.name.isBlank() ? String.join(", ", group.members) : group.name;
	}

	/** The groups that this mob belongs to, in priority order. */
	public static List<Source> groupSources(CmsConfig config, EntityType<?> type) {
		List<Source> sources = new ArrayList<>();
		for (CmsConfig.MobGroup group : config.groups) {
			if (isMember(group, type)) {
				sources.add(new Source(groupName(group), group.settings));
			}
		}
		return sources;
	}

	/** Every level for this mob, most specific first; empty when nothing is configured for it. */
	public static List<Source> sources(CmsConfig config, EntityType<?> type) {
		List<Source> sources = new ArrayList<>();
		CmsConfig.MobSettings own = config.mobs.get(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		if (own != null) {
			sources.add(new Source("", own));
			if (own.ignoreGroups) {
				return sources;
			}
		}
		sources.addAll(groupSources(config, type));
		return sources;
	}

	/** The settings that apply to this mob, or null when nothing is configured for it. */
	public static CmsConfig.@Nullable MobSettings resolve(CmsConfig config, EntityType<?> type) {
		List<Source> sources = sources(config, type);
		return sources.isEmpty() ? null : merge(sources);
	}

	public static CmsConfig.MobSettings merge(List<Source> levels) {
		CmsConfig.MobSettings result = new CmsConfig.MobSettings();
		for (Entry entry : entries(levels)) {
			entry.apply().accept(result);
		}
		return result;
	}

	/** What these levels give, each value with the level it comes from. */
	public static List<Inherited> inherited(List<Source> levels) {
		return entries(levels).stream().map(Entry::info).toList();
	}

	private static List<Entry> entries(List<Source> levels) {
		List<Entry> entries = new ArrayList<>();
		scalar(entries, levels, SPAWN_RATE, settings -> settings.spawnRate != 100.0, settings -> number(settings.spawnRate),
			(from, to) -> to.spawnRate = from.spawnRate);
		scalar(entries, levels, BANNED, settings -> settings.banned, settings -> "true", (from, to) -> to.banned = true);
		scalar(entries, levels, DESPAWN, settings -> settings.despawn != null && settings.despawn != CmsConfig.DespawnMode.DEFAULT,
			settings -> settings.despawn.name(), (from, to) -> to.despawn = from.despawn);
		scalar(entries, levels, SPAWN_RULE, settings -> settings.spawnRule != null && settings.spawnRule.enabled, settings -> "",
			(from, to) -> to.spawnRule = from.spawnRule);

		for (Source level : levels) {
			CmsConfig.MobSettings settings = level.settings();
			for (String dimension : settings.bannedDimensions) {
				list(entries, level, key(BANNED_DIMENSIONS, dimension), dimension, to -> to.bannedDimensions.add(dimension));
			}
			for (String biome : settings.bannedBiomes) {
				list(entries, level, key(BANNED_BIOMES, biome), biome, to -> to.bannedBiomes.add(biome));
			}
			for (String reason : settings.disabledReasons) {
				list(entries, level, key(DISABLED_REASONS, reason), reason, to -> to.disabledReasons.add(reason));
			}
			Map<String, List<CmsConfig.NbtBan>> bans = new LinkedHashMap<>();
			settings.nbtBans.forEach(ban -> bans.computeIfAbsent(ban.path, k -> new ArrayList<>()).add(ban));
			bans.forEach((path, list) -> list(entries, level, key(NBT_BANS, path),
				path + " = " + String.join(" | ", list.stream().map(ban -> ban.value).toList()), to -> to.nbtBans.addAll(list)));
			// Proportions of one path come from a single level: from several, they could add up to more than 100 %.
			Map<String, List<CmsConfig.NbtProportion>> proportions = new LinkedHashMap<>();
			settings.nbtProportions.forEach(proportion -> proportions.computeIfAbsent(proportion.path, k -> new ArrayList<>()).add(proportion));
			proportions.forEach((path, list) -> list(entries, level, key(NBT_PROPORTIONS, path),
				path + ": " + String.join(", ", list.stream().map(proportion -> proportion.value + " " + number(proportion.percent) + " %").toList()),
				to -> to.nbtProportions.addAll(list)));
		}
		return entries;
	}

	private interface Copy {
		void copy(CmsConfig.MobSettings from, CmsConfig.MobSettings to);
	}

	private static void scalar(List<Entry> entries, List<Source> levels, String key, Predicate<CmsConfig.MobSettings> changed,
		Function<CmsConfig.MobSettings, String> text, Copy copy) {
		for (Source level : levels) {
			CmsConfig.MobSettings settings = level.settings();
			if (changed.test(settings)) {
				entries.add(new Entry(new Inherited(key, text.apply(settings), level.name()), to -> copy.copy(settings, to)));
				return;
			}
		}
	}

	/**
	 * Adds an entry unless a more specific level already gave it. NBT bans on the same path add up;
	 * for anything else the most specific level wins.
	 */
	private static void list(List<Entry> entries, Source level, String key, String value, Consumer<CmsConfig.MobSettings> apply) {
		if (!key.startsWith(NBT_BANS + ":") && entries.stream().anyMatch(entry -> entry.info().key().equals(key))) {
			return;
		}
		entries.add(new Entry(new Inherited(key, value, level.name()), apply));
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
	}
}
