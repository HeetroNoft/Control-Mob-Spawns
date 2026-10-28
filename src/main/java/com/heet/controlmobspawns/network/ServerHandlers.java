package com.heet.controlmobspawns.network;

import com.heet.controlmobspawns.ControlMobSpawns;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.spawn.ExistingMobCleaner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class ServerHandlers {
	private ServerHandlers() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(CmsPayloads.RequestSync.TYPE, (payload, context) -> sendSync(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CmsPayloads.Save.TYPE, (payload, context) -> save(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(CmsPayloads.RequestMobCounts.TYPE, (payload, context) -> sendMobCounts(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CmsPayloads.DespawnAll.TYPE, (payload, context) -> despawnAll(context.player(), payload));
	}

	public static boolean canEdit(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server.isSingleplayerOwner(player.nameAndId())) {
			return true;
		}
		PermissionLevel level = PermissionLevel.byId(ConfigManager.active().raw.permissionLevel);
		return player.permissions().hasPermission(new Permission.HasCommandLevel(level));
	}

	private static void sendSync(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<String> dimensions = server.levelKeys().stream().map(key -> key.identifier().toString()).sorted().toList();
		var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
		List<String> biomes = biomeRegistry.listElementIds().map(key -> key.identifier().toString()).sorted().toList();
		List<String> biomeTags = biomeRegistry.listTagIds().map(tag -> tag.location().toString()).sorted().toList();
		ServerPlayNetworking.send(player, new CmsPayloads.Sync(
			ConfigManager.toJsonString(ConfigManager.globalConfig()),
			ConfigManager.toJsonString(ConfigManager.effectiveConfig()),
			canEdit(player), dimensions, biomes, biomeTags));
	}

	private static void save(ServerPlayer player, CmsPayloads.Save payload) {
		if (!canEdit(player)) {
			player.sendSystemMessage(Component.translatable("controlmobspawns.message.no_permission"));
			return;
		}
		CmsConfig config;
		try {
			config = ConfigManager.fromJsonString(payload.json());
		} catch (Exception e) {
			ControlMobSpawns.LOGGER.warn("Invalid configuration received from {}", player.getPlainTextName(), e);
			return;
		}
		if (payload.world()) {
			ConfigManager.saveWorld(config);
		} else {
			ConfigManager.saveGlobal(config);
		}
		ControlMobSpawns.LOGGER.info("{} saved the {} configuration", player.getPlainTextName(), payload.world() ? "world" : "global");
		ExistingMobCleaner.sweep(player.level().getServer());
		player.sendSystemMessage(Component.translatable("controlmobspawns.message.saved"));
		sendSync(player);
	}

	private static void sendMobCounts(ServerPlayer player) {
		List<CmsPayloads.MobCount> counts = new ArrayList<>();
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			Map<EntityType<?>, Integer> perType = new HashMap<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob && entity.isAlive()) {
					perType.merge(entity.getType(), 1, Integer::sum);
				}
			}
			String dimension = level.dimension().identifier().toString();
			perType.forEach((type, count) -> counts.add(
				new CmsPayloads.MobCount(dimension, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), count)));
		}
		counts.sort(Comparator.comparingInt(CmsPayloads.MobCount::count).reversed());
		ServerPlayNetworking.send(player, new CmsPayloads.MobCounts(counts));
	}

	private static void despawnAll(ServerPlayer player, CmsPayloads.DespawnAll payload) {
		if (!canEdit(player)) {
			player.sendSystemMessage(Component.translatable("controlmobspawns.message.no_permission"));
			return;
		}
		Identifier typeId = Identifier.tryParse(payload.entity());
		EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
		if (type == null) {
			return;
		}
		int removed = 0;
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			if (!payload.dimension().isEmpty() && !level.dimension().identifier().toString().equals(payload.dimension())) {
				continue;
			}
			List<Entity> targets = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity.getType() == type && !(entity instanceof Player)) {
					targets.add(entity);
				}
			}
			for (Entity entity : targets) {
				entity.discard();
			}
			removed += targets.size();
		}
		player.sendSystemMessage(Component.translatable("controlmobspawns.message.despawned", removed, type.getDescription()));
		sendMobCounts(player);
	}
}
