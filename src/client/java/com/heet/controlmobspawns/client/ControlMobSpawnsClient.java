package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

public class ControlMobSpawnsClient implements ClientModInitializer {
	private static @Nullable Consumer<CmsPayloads.Sync> syncListener;
	private static @Nullable Consumer<List<CmsPayloads.MobCount>> mobCountListener;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(CmsPayloads.Sync.TYPE, (payload, context) -> {
			if (syncListener != null) {
				syncListener.accept(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CmsPayloads.MobCounts.TYPE, (payload, context) -> {
			if (mobCountListener != null) {
				mobCountListener.accept(payload.counts());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(CmsPayloads.NbtPaths.TYPE, (payload, context) -> NbtSuggestions.accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NbtSuggestions.clear());
	}

	public static void setSyncListener(@Nullable Consumer<CmsPayloads.Sync> listener) {
		syncListener = listener;
	}

	public static void setMobCountListener(@Nullable Consumer<List<CmsPayloads.MobCount>> listener) {
		mobCountListener = listener;
	}
}
