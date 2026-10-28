package com.heet.controlmobspawns.network;

import com.heet.controlmobspawns.ControlMobSpawns;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class CmsPayloads {
	private static final int MAX_STRING = 8 * 1024 * 1024;
	private static final int MAX_PACKET = 16 * 1024 * 1024;
	private static final StreamCodec<ByteBuf, String> LONG_STRING = ByteBufCodecs.stringUtf8(MAX_STRING);
	private static final StreamCodec<ByteBuf, List<String>> STRING_LIST = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

	private CmsPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(ControlMobSpawns.id(path));
	}

	/** Client asks for the configuration. */
	public record RequestSync() implements CustomPacketPayload {
		public static final Type<RequestSync> TYPE = payloadType("request_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestSync> CODEC = StreamCodec.unit(new RequestSync());

		@Override
		public Type<RequestSync> type() {
			return TYPE;
		}
	}

	/** Server sends the global and effective configuration plus the ids the screens offer. */
	public record Sync(
		String globalJson, String effectiveJson, boolean canEdit, List<String> dimensions, List<String> biomes, List<String> biomeTags
	) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = payloadType("sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
			LONG_STRING, Sync::globalJson,
			LONG_STRING, Sync::effectiveJson,
			ByteBufCodecs.BOOL, Sync::canEdit,
			STRING_LIST, Sync::dimensions,
			STRING_LIST, Sync::biomes,
			STRING_LIST, Sync::biomeTags,
			Sync::new
		);

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Client saves a configuration, either the global file or this world's override. */
	public record Save(boolean world, String json) implements CustomPacketPayload {
		public static final Type<Save> TYPE = payloadType("save");
		public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Save::world,
			LONG_STRING, Save::json,
			Save::new
		);

		@Override
		public Type<Save> type() {
			return TYPE;
		}
	}

	public record RequestMobCounts() implements CustomPacketPayload {
		public static final Type<RequestMobCounts> TYPE = payloadType("request_mob_counts");
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestMobCounts> CODEC = StreamCodec.unit(new RequestMobCounts());

		@Override
		public Type<RequestMobCounts> type() {
			return TYPE;
		}
	}

	public record MobCount(String dimension, String entity, int count) {
		public static final StreamCodec<ByteBuf, MobCount> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MobCount::dimension,
			ByteBufCodecs.STRING_UTF8, MobCount::entity,
			ByteBufCodecs.VAR_INT, MobCount::count,
			MobCount::new
		);
	}

	public record MobCounts(List<MobCount> counts) implements CustomPacketPayload {
		public static final Type<MobCounts> TYPE = payloadType("mob_counts");
		public static final StreamCodec<RegistryFriendlyByteBuf, MobCounts> CODEC = StreamCodec.composite(
			MobCount.CODEC.apply(ByteBufCodecs.list()), MobCounts::counts,
			MobCounts::new
		);

		@Override
		public Type<MobCounts> type() {
			return TYPE;
		}
	}

	/** Removes every entity of a type, in one dimension or in all of them when the dimension is empty. */
	public record DespawnAll(String entity, String dimension) implements CustomPacketPayload {
		public static final Type<DespawnAll> TYPE = payloadType("despawn_all");
		public static final StreamCodec<RegistryFriendlyByteBuf, DespawnAll> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, DespawnAll::entity,
			ByteBufCodecs.STRING_UTF8, DespawnAll::dimension,
			DespawnAll::new
		);

		@Override
		public Type<DespawnAll> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RequestSync.TYPE, RequestSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().registerLarge(Save.TYPE, Save.CODEC, MAX_PACKET);
		PayloadTypeRegistry.serverboundPlay().register(RequestMobCounts.TYPE, RequestMobCounts.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DespawnAll.TYPE, DespawnAll.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Sync.TYPE, Sync.CODEC, MAX_PACKET);
		PayloadTypeRegistry.clientboundPlay().registerLarge(MobCounts.TYPE, MobCounts.CODEC, MAX_PACKET);
	}
}
