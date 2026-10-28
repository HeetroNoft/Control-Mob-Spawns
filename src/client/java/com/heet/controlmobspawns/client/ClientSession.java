package com.heet.controlmobspawns.client;

import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.level.Level;

/**
 * A configuration being edited. Remote sessions are saved on the server; local ones (from the
 * title screen) write the global file directly.
 */
public final class ClientSession {
	public enum Scope {
		GLOBAL, WORLD
	}

	public final Scope scope;
	public final CmsConfig config;
	public final boolean remote;
	public final List<String> dimensions;
	public final List<String> biomes;
	public final List<String> biomeTags;

	private ClientSession(Scope scope, CmsConfig config, boolean remote, List<String> dimensions, List<String> biomes, List<String> biomeTags) {
		this.scope = scope;
		this.config = config;
		this.remote = remote;
		this.dimensions = dimensions;
		this.biomes = biomes;
		this.biomeTags = biomeTags;
	}

	public static ClientSession local() {
		List<String> dimensions = List.of(Level.OVERWORLD.identifier().toString(), Level.NETHER.identifier().toString(), Level.END.identifier().toString());
		return new ClientSession(Scope.GLOBAL, ConfigManager.globalConfig(), false, dimensions, List.of(), List.of());
	}

	public static ClientSession remote(Scope scope, CmsPayloads.Sync sync) {
		String json = scope == Scope.WORLD ? sync.effectiveJson() : sync.globalJson();
		return new ClientSession(scope, ConfigManager.fromJsonString(json), true, sync.dimensions(), sync.biomes(), sync.biomeTags());
	}

	public void save() {
		CmsConfig copy = ConfigManager.copy(config);
		if (remote) {
			ClientPlayNetworking.send(new CmsPayloads.Save(scope == Scope.WORLD, ConfigManager.toJsonString(copy)));
		} else {
			ConfigManager.saveGlobal(copy);
		}
	}
}
