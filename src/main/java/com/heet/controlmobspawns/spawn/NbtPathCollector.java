package com.heet.controlmobspawns.spawn;

import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

/**
 * Lists the NBT paths of a mob, with the values seen on a freshly created mob and on the
 * mobs of that type already in the world.
 */
public final class NbtPathCollector {
	/** Values that are unique to each entity or change constantly: useless as conditions. */
	private static final Set<String> EXCLUDED = Set.of("UUID", "Pos", "Motion", "Rotation", "Brain");
	private static final int MAX_EXISTING = 200;
	private static final int MAX_DEPTH = 5;
	private static final int MAX_LIST_ELEMENTS = 3;
	private static final int MAX_VALUES = 20;
	private static final int MAX_VALUE_LENGTH = 64;

	private NbtPathCollector() {
	}

	public static List<CmsPayloads.NbtPathInfo> collect(MinecraftServer server, EntityType<?> type) {
		Map<String, Set<String>> paths = new TreeMap<>();
		ServerLevel overworld = server.overworld();
		Entity sample = type.create(overworld, EntitySpawnReason.NATURAL);
		if (sample != null) {
			sample.snapTo(overworld.getRespawnData().pos(), 0, 0);
			collect(SpawnControl.save(sample), "", 0, paths);
			sample.discard();
		}
		int seen = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (seen >= MAX_EXISTING) {
					break;
				}
				if (entity.getType() == type) {
					collect(SpawnControl.save(entity), "", 0, paths);
					seen++;
				}
			}
		}
		List<CmsPayloads.NbtPathInfo> result = new ArrayList<>();
		paths.forEach((path, values) -> result.add(new CmsPayloads.NbtPathInfo(path, List.copyOf(values))));
		return result;
	}

	private static void collect(Tag tag, String path, int depth, Map<String, Set<String>> paths) {
		if (depth > MAX_DEPTH) {
			return;
		}
		if (tag instanceof CompoundTag compound) {
			if (!path.isEmpty()) {
				addValue(paths, path, tag);
			}
			for (String key : compound.keySet()) {
				if (depth == 0 && EXCLUDED.contains(key)) {
					continue;
				}
				Tag child = compound.get(key);
				if (child != null) {
					collect(child, path.isEmpty() ? quote(key) : path + "." + quote(key), depth + 1, paths);
				}
			}
		} else if (tag instanceof ListTag list) {
			addValue(paths, path, tag);
			for (int i = 0; i < Math.min(list.size(), MAX_LIST_ELEMENTS); i++) {
				collect(list.get(i), path + "[" + i + "]", depth + 1, paths);
			}
		} else {
			addValue(paths, path, tag);
			if (tag instanceof ByteTag flag && (flag.value() == 0 || flag.value() == 1)) {
				addValue(paths, path, ByteTag.valueOf(flag.value() == 0));
			}
		}
	}

	private static void addValue(Map<String, Set<String>> paths, String path, Tag tag) {
		Set<String> values = paths.computeIfAbsent(path, key -> new LinkedHashSet<>());
		String value = tag.toString();
		if (values.size() < MAX_VALUES && value.length() <= MAX_VALUE_LENGTH) {
			values.add(value);
		}
	}

	private static String quote(String key) {
		for (char c : key.toCharArray()) {
			if (c == ' ' || c == '"' || c == '\'' || c == '[' || c == ']' || c == '.' || c == '{' || c == '}') {
				return '"' + key.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
			}
		}
		return key;
	}
}
