package com.heet.controlmobspawns.test;

import com.heet.controlmobspawns.ControlMobSpawns;
import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.client.ModMenuIntegration;
import com.heet.controlmobspawns.client.NbtSuggestions;
import com.heet.controlmobspawns.client.WorldConfigKey;
import com.heet.controlmobspawns.client.screen.HomeScreen;
import com.heet.controlmobspawns.client.screen.MobDetailScreen;
import com.heet.controlmobspawns.client.screen.MainConfigScreen;
import com.heet.controlmobspawns.client.screen.NbtBansScreen;
import com.heet.controlmobspawns.client.screen.NbtPathPickerScreen;
import com.heet.controlmobspawns.client.screen.NbtProportionsScreen;
import com.heet.controlmobspawns.client.screen.RowList;
import com.heet.controlmobspawns.client.screen.SpawnRuleScreen;
import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.ConfigManager;
import com.heet.controlmobspawns.mixin.MobCategoryAccessor;
import com.heet.controlmobspawns.network.CmsPayloads;
import com.heet.controlmobspawns.spawn.ExistingMobCleaner;
import com.heet.controlmobspawns.spawn.ItemPickupHolder;
import com.heet.controlmobspawns.spawn.SpawnControl;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

public class CmsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		screensFromTitle(context);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			bans(server);
			nbtProportions(server);
			spawnLists(server);
			spawnRules(server);
			groups(server);
			despawn(server);
			existingMobs(context, server);
			networking(context, server);
			screensInWorld(context, server);
		}
		dedicatedServer(context);
		ControlMobSpawns.LOGGER.info("[CMS-TEST] All Control Mob Spawns client game tests passed");
	}

	private static void screensFromTitle(ClientGameTestContext context) {
		Screen title = context.computeOnClient(minecraft -> new TitleScreen());
		context.setScreen(() -> ModMenuIntegration.openRoot(title));
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MainConfigScreen);
		context.waitTicks(3);
		context.takeScreenshot("cms-01-general-tab");
		context.getInput().typeChars("Perm");
		context.getInput().setCursorPos(500, 208);
		context.waitTicks(3);
		context.takeScreenshot("cms-01b-general-reset-hover");
		ClientSession session = context.computeOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).session());
		check(context.computeOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).tabCount()) == 4, "no Current mobs tab from the title screen");
		context.runOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).showTab(1));
		context.getInput().setCursorPos(500, 165);
		context.waitTicks(3);
		context.takeScreenshot("cms-02-mobs-tab-hover");
		context.setScreen(() -> new MobDetailScreen(title, session, EntityTypes.ZOMBIE));
		context.takeScreenshot("cms-03-mob-detail");
		CmsConfig.MobSettings zombie = session.config.getOrCreateMob("minecraft:zombie");
		CmsConfig.NbtProportion baby = new CmsConfig.NbtProportion();
		baby.path = "IsBaby";
		baby.value = "1b";
		baby.percent = 20;
		zombie.nbtProportions.add(baby);
		context.setScreen(() -> new NbtProportionsScreen(title, session, "minecraft:zombie", zombie));
		context.takeScreenshot("cms-04-nbt-proportions");
		context.setScreen(() -> new SpawnRuleScreen(title, session, zombie));
		context.takeScreenshot("cms-05-spawn-rule");
		session.config.biomeSpawns.add(new CmsConfig.BiomeSpawn());
		session.config.biomeSpawns.getFirst().entity = "minecraft:blaze";
		session.config.biomeSpawns.getFirst().biomes.add("minecraft:plains");
		context.setScreen(() -> new MainConfigScreen(title, session));
		context.runOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).showTab(3));
		context.waitTicks(2);
		context.takeScreenshot("cms-06-biome-spawns-tab");
		context.setScreen(() -> title);
	}

	private static void bans(TestServerContext server) {
		setWorldConfig(server, config -> {
			config.coverage.eggsAndCommands = true;
			config.getOrCreateMob("minecraft:husk").banned = true;
		});
		summonAndExpect(server, "minecraft:husk", EntityTypes.HUSK, 0, "global ban with commands covered");
		check(!ConfigManager.globalConfig().mobs.containsKey("minecraft:husk"), "world override must not change the global file");

		setWorldConfig(server, config -> config.coverage.eggsAndCommands = false);
		summonAndExpect(server, "minecraft:husk", EntityTypes.HUSK, 1, "commands not covered");
		server.runCommand("kill @e[type=minecraft:husk]");

		setWorldConfig(server, config -> config.getOrCreateMob("minecraft:cow").disabledReasons.add("COMMAND"));
		summonAndExpect(server, "minecraft:cow", EntityTypes.COW, 0, "disabled COMMAND spawn type");

		setWorldConfig(server, config -> {
			config.coverage.eggsAndCommands = true;
			config.getOrCreateMob("minecraft:pig").bannedBiomes.add("#minecraft:is_overworld");
			config.getOrCreateMob("minecraft:sheep").bannedDimensions.add("minecraft:overworld");
		});
		summonAndExpect(server, "minecraft:pig", EntityTypes.PIG, 0, "biome tag ban");
		summonAndExpect(server, "minecraft:sheep", EntityTypes.SHEEP, 0, "dimension ban");

		setWorldConfig(server, config -> {
			CmsConfig.NbtBan ban = new CmsConfig.NbtBan();
			ban.path = "IsBaby";
			ban.value = "1b";
			config.getOrCreateMob("minecraft:zombie").nbtBans.add(ban);
		});
		server.runCommand("summon minecraft:zombie ~ ~ ~ {IsBaby:1b}");
		check(count(server, EntityTypes.ZOMBIE) == 0, "NBT ban must block baby zombies");
		server.runCommand("summon minecraft:zombie ~ ~ ~ {IsBaby:0b}");
		check(count(server, EntityTypes.ZOMBIE) == 1, "NBT ban must let adult zombies through");
		server.runCommand("kill @e[type=minecraft:zombie]");
		resetWorldConfig(server);
	}

	private static void nbtProportions(TestServerContext server) {
		setWorldConfig(server, config -> {
			CmsConfig.NbtProportion baby = new CmsConfig.NbtProportion();
			baby.path = "IsBaby";
			baby.value = "1b";
			baby.percent = 30;
			config.getOrCreateMob("minecraft:zombie").nbtProportions.add(baby);
		});
		int[] result = server.computeOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			BlockPos pos = surface(level, level.getRespawnData().pos());
			int babies = 0;
			int total = 400;
			for (int i = 0; i < total; i++) {
				Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.NATURAL);
				zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
				zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
				level.addFreshEntity(zombie);
				if (zombie.isBaby()) {
					babies++;
				}
			}
			return new int[]{babies, total};
		});
		double ratio = (double) result[0] / result[1];
		ControlMobSpawns.LOGGER.info("[CMS-TEST] NBT proportion: {} babies out of {} ({})", result[0], result[1], ratio);
		check(ratio > 0.22 && ratio < 0.38, "about 30% of zombies must be babies, got " + ratio);
		server.runCommand("kill @e[type=!minecraft:player]");
		resetWorldConfig(server);
	}

	private static void spawnLists(TestServerContext server) {
		setWorldConfig(server, config -> {
			config.getOrCreateMob("minecraft:zombie").spawnRate = 1000;
			CmsConfig.BiomeSpawn blaze = new CmsConfig.BiomeSpawn();
			blaze.entity = "minecraft:blaze";
			blaze.biomes.add("#minecraft:is_overworld");
			blaze.weight = 7;
			config.biomeSpawns.add(blaze);
			config.mobCaps.put("monster", 5);
		});
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			Holder<Biome> biome = level.getBiome(level.getRespawnData().pos());
			WeightedList<MobSpawnSettings.SpawnerData> original = biome.value().getMobSettings().getMobs(MobCategory.MONSTER);
			WeightedList<MobSpawnSettings.SpawnerData> adjusted = SpawnControl.adjustSpawnList(original, level.dimension(), biome, MobCategory.MONSTER, EntitySpawnReason.NATURAL);
			int zombieBefore = weight(original, EntityTypes.ZOMBIE);
			int skeletonBefore = weight(original, EntityTypes.SKELETON);
			check(zombieBefore > 0 && skeletonBefore > 0, "test biome must spawn zombies and skeletons, biome " + biome.getRegisteredName());
			check(weight(adjusted, EntityTypes.ZOMBIE) == zombieBefore * 1000, "zombie weight x10 (scaled by 100)");
			check(weight(adjusted, EntityTypes.SKELETON) == skeletonBefore * 100, "other weights scaled by 100");
			check(weight(adjusted, EntityTypes.BLAZE) == 700, "biome spawn added with its weight");
			check(MobCategory.MONSTER.getMaxInstancesPerChunk() == 5, "monster cap overridden");
			check(((MobCategoryAccessor) (Object) MobCategory.MONSTER).cms$getVanillaMax() == 70, "vanilla cap still readable");
		});
		resetWorldConfig(server);
		server.runOnServer(minecraftServer -> check(MobCategory.MONSTER.getMaxInstancesPerChunk() == 70, "monster cap restored"));
	}

	private static void spawnRules(TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			BlockPos pos = surface(level, level.getRespawnData().pos());
			check(!SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, level.getRandom()),
				"vanilla: no zombie on the surface at noon");
		});
		setWorldConfig(server, config -> config.getOrCreateMob("minecraft:zombie").spawnRule = new CmsConfig.SpawnRule());
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			BlockPos pos = surface(level, level.getRespawnData().pos());
			check(SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, level.getRandom()),
				"empty rule allows zombies at noon");
		});
		setWorldConfig(server, config -> {
			CmsConfig.SpawnRule rule = new CmsConfig.SpawnRule();
			rule.time = CmsConfig.TimeOfDay.NIGHT;
			config.getOrCreateMob("minecraft:zombie").spawnRule = rule;
		});
		server.runOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			BlockPos pos = surface(level, level.getRespawnData().pos());
			check(!SpawnPlacements.checkSpawnRules(EntityTypes.ZOMBIE, level, EntitySpawnReason.NATURAL, pos, level.getRandom()),
				"night-only rule refuses at noon");
		});
		resetWorldConfig(server);
		naturalSpawning(server);
	}

	/**
	 * Runs vanilla's whole natural spawn loop, not only the placement predicate: the mob's own
	 * checks (light for monsters) must not undo a spawn rule.
	 */
	private static void naturalSpawning(TestServerContext server) {
		for (EntityType<?> type : List.of(EntityTypes.ZOMBIE, EntityTypes.WARDEN)) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
			check(naturalSpawns(server, type) == 0, "vanilla: no natural " + id + " on the surface at noon");
			setWorldConfig(server, config -> {
				config.getOrCreateMob(id).spawnRule = new CmsConfig.SpawnRule();
				CmsConfig.BiomeSpawn spawn = new CmsConfig.BiomeSpawn();
				spawn.entity = id;
				spawn.biomes.add("#minecraft:is_overworld");
				spawn.weight = 100_000;
				config.biomeSpawns.add(spawn);
			});
			int spawned = naturalSpawns(server, type);
			check(spawned > 0, "empty rule + biome spawn: " + id + " spawns naturally at noon (" + spawned + ")");
			resetWorldConfig(server);
		}
	}

	/** Runs the natural monster spawn loop around the player and returns how many mobs of this type appeared. */
	private static int naturalSpawns(TestServerContext server, EntityType<?> type) {
		return server.computeOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			Player player = level.players().getFirst();
			level.getEntities(type, entity -> true).forEach(entity -> entity.discard());
			for (int angle = 0; angle < 360; angle += 10) {
				double radians = Math.toRadians(angle);
				BlockPos target = player.blockPosition().offset((int) (Math.cos(radians) * 48), 0, (int) (Math.sin(radians) * 48));
				for (int attempt = 0; attempt < 10; attempt++) {
					net.minecraft.world.level.NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, surface(level, target));
				}
			}
			int found = level.getEntities(type, entity -> true).size();
			// Also removes the chickens of chicken jockeys.
			level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Mob.class), entity -> true)
				.forEach(entity -> entity.discard());
			return found;
		});
	}

	/** Mob > groups > tags, merged lists and forced vanilla values. */
	private static void groups(TestServerContext server) {
		setWorldConfig(server, config -> {
			CmsConfig.MobGroup undead = new CmsConfig.MobGroup();
			undead.members.add("#minecraft:undead");
			undead.settings.spawnRate = 300;
			undead.settings.bannedBiomes.add("#minecraft:is_ocean");
			CmsConfig.NbtProportion baby = new CmsConfig.NbtProportion();
			baby.path = "IsBaby";
			baby.value = "1b";
			baby.percent = 100;
			undead.settings.nbtProportions.add(baby);
			CmsConfig.MobGroup lowerTag = new CmsConfig.MobGroup();
			lowerTag.members.add("#minecraft:skeletons");
			lowerTag.settings.spawnRate = 20;
			lowerTag.settings.bannedBiomes.add("minecraft:desert");
			CmsConfig.MobGroup creatures = new CmsConfig.MobGroup();
			creatures.members.add("@creature");
			creatures.settings.banned = true;
			config.tagGroups.addAll(List.of(undead, lowerTag, creatures));

			CmsConfig.MobGroup zombies = new CmsConfig.MobGroup();
			zombies.name = "Zombies";
			zombies.members.add("minecraft:zombie");
			zombies.settings.spawnRate = 50;
			zombies.settings.despawn = CmsConfig.DespawnMode.NEVER;
			config.groups.add(zombies);

			CmsConfig.MobSettings zombie = config.getOrCreateMob("minecraft:zombie");
			zombie.forceVanilla.add("nbtProportions:IsBaby");
			zombie.forceVanilla.add("bannedBiomes:#minecraft:is_ocean");
			config.getOrCreateMob("minecraft:husk").spawnRate = 200;
			config.getOrCreateMob("minecraft:drowned").forceVanilla.add("spawnRate");
		});
		server.runOnServer(minecraftServer -> {
			var active = ConfigManager.active();
			var skeleton = active.mob(EntityTypes.SKELETON);
			check(skeleton != null && skeleton.spawnRate == 300, "tag: undead skeletons get 300 %, the first tag wins over a later one");
			check(skeleton.proportions.size() == 1, "tag: skeletons get the baby proportion");
			check(skeleton.bannedBiomes.matches(minecraftServer.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
				.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT)), "tag lists merge: skeletons also banned in the desert");
			var zombie = active.mob(EntityTypes.ZOMBIE);
			check(zombie.spawnRate == 50 && zombie.despawn == CmsConfig.DespawnMode.NEVER, "group wins over tag");
			check(zombie.proportions.isEmpty(), "mob forces the inherited proportion back to vanilla");
			check(!zombie.bannedBiomes.matches(minecraftServer.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
				.getOrThrow(net.minecraft.world.level.biome.Biomes.OCEAN)), "mob forces an inherited biome ban back to vanilla");
			check(active.mob(EntityTypes.HUSK).spawnRate == 200, "mob wins over tag");
			check(active.mob(EntityTypes.DROWNED).spawnRate == 100.0, "mob forces the spawn rate back to vanilla");
			check(active.mob(EntityTypes.COW).banned, "category group: cows are creatures");
			check(active.mob(EntityTypes.CREEPER) == null, "mobs outside every group stay vanilla");
		});
		resetWorldConfig(server);
	}

	private static void despawn(TestServerContext server) {
		check(despawned(server, zombie -> { }), "vanilla: far zombie despawns");
		check(!despawned(server, Zombie::setPersistenceRequired), "vanilla: persistent zombie stays");

		setWorldConfig(server, config -> config.getOrCreateMob("minecraft:zombie").despawn = CmsConfig.DespawnMode.NEVER);
		check(!despawned(server, zombie -> { }), "NEVER keeps the zombie");

		setWorldConfig(server, config -> config.getOrCreateMob("minecraft:zombie").despawn = CmsConfig.DespawnMode.FORCE);
		check(despawned(server, Zombie::setPersistenceRequired), "FORCE removes a persistent zombie");
		check(!despawned(server, zombie -> {
			zombie.setPersistenceRequired();
			zombie.setCustomName(net.minecraft.network.chat.Component.literal("Bob"));
		}), "FORCE spares named zombies by default");

		Consumer<Zombie> pickedUp = zombie -> {
			zombie.setPersistenceRequired();
			((ItemPickupHolder) zombie).cms$setPersistentFromPickup(true);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
			zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		};
		resetWorldConfig(server);
		check(!despawned(server, pickedUp), "vanilla: zombie that picked up an item stays");
		setWorldConfig(server, config -> config.despawn.itemPickersDespawn = true);
		check(despawned(server, pickedUp), "toggle: zombie that picked up an item despawns");
		resetWorldConfig(server);
	}

	/** Creates a zombie 150 blocks from the player and runs one despawn check. */
	private static boolean despawned(TestServerContext server, Consumer<Zombie> setup) {
		return server.computeOnServer(minecraftServer -> {
			ServerLevel level = minecraftServer.overworld();
			Player player = level.players().getFirst();
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.NATURAL);
			zombie.snapTo(player.getX() + 150, player.getY(), player.getZ(), 0, 0);
			setup.accept(zombie);
			zombie.checkDespawn();
			if (zombie.isRemoved() && ((ItemPickupHolder) zombie).cms$isPersistentFromPickup()) {
				check(zombie.getMainHandItem().isEmpty(), "picked-up item must be dropped on despawn");
			}
			return zombie.isRemoved();
		});
	}

	private static void existingMobs(ClientGameTestContext context, TestServerContext server) {
		for (int i = 0; i < 3; i++) {
			server.runCommand("summon minecraft:sheep ~ ~ ~");
		}
		check(count(server, EntityTypes.SHEEP) == 3, "three sheep summoned");
		setWorldConfig(server, config -> {
			config.coverage.removeExisting = true;
			config.getOrCreateMob("minecraft:sheep").banned = true;
		});
		server.runOnServer(ExistingMobCleaner::sweep);
		context.waitTicks(3);
		check(count(server, EntityTypes.SHEEP) == 0, "existing banned sheep removed");
		resetWorldConfig(server);
	}

	private static void networking(ClientGameTestContext context, TestServerContext server) {
		for (int i = 0; i < 5; i++) {
			server.runCommand("summon minecraft:chicken ~ ~ ~");
		}
		check(count(server, EntityTypes.CHICKEN) == 5, "five chickens summoned");
		context.runOnClient(minecraft -> ClientPlayNetworking.send(new CmsPayloads.DespawnAll("minecraft:chicken", "")));
		server.waitFor(minecraftServer -> count(minecraftServer, EntityTypes.CHICKEN) == 0);

		CmsConfig effective = ConfigManager.effectiveConfig();
		effective.getOrCreateMob("minecraft:creeper").spawnRate = 250;
		String json = ConfigManager.toJsonString(effective);
		context.runOnClient(minecraft -> ClientPlayNetworking.send(new CmsPayloads.Save(true, json)));
		server.waitFor(minecraftServer -> ConfigManager.active().mob(EntityTypes.CREEPER) != null);
		check(ConfigManager.active().mob(EntityTypes.CREEPER).spawnRate == 250, "world save over the network");
		check(!ConfigManager.globalConfig().mobs.containsKey("minecraft:creeper"), "global untouched by a world save");
		String worldFile = server.computeOnServer(minecraftServer -> readWorldFile(minecraftServer));
		ControlMobSpawns.LOGGER.info("[CMS-TEST] World override file: {}", worldFile.replace('\n', ' '));
		check(worldFile.contains("creeper") && !worldFile.contains("permissionLevel"), "world file holds only the differences");
		resetWorldConfig(server);
	}

	private static void screensInWorld(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon minecraft:cow ~ ~ ~");
		server.runCommand("summon minecraft:cow ~ ~ ~");
		server.runCommand("summon minecraft:zombie ~ ~ ~ {NoAI:1b}");
		context.setScreen(() -> ModMenuIntegration.openRoot(null));
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof HomeScreen home && home.canEdit() != null);
		check(context.computeOnClient(minecraft -> ((HomeScreen) minecraft.gui.screen()).canEdit()), "singleplayer owner can edit");
		context.takeScreenshot("cms-07-home-singleplayer");
		context.clickScreenButton("controlmobspawns.gui.home.world");
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MainConfigScreen);
		check(context.computeOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).tabCount()) == 5, "the world screen has a Current mobs tab");
		context.runOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).showTab(4));
		context.waitTicks(10);
		context.takeScreenshot("cms-08-current-mobs-tab");
		context.getInput().setCursorPos(500, 210);
		context.waitTicks(3);
		context.takeScreenshot("cms-09-current-mobs-hover");

		ClientSession worldSession = context.computeOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).session());
		check(!context.computeOnClient(minecraft -> worldSession.isDirty()), "no unsaved change after opening");
		context.runOnClient(minecraft -> worldSession.config.getOrCreateMob("minecraft:cow").banned = true);
		context.waitTicks(2);
		context.clickScreenButton("controlmobspawns.gui.save");
		server.waitFor(minecraftServer -> ConfigManager.active().mob(EntityTypes.COW) != null && ConfigManager.active().mob(EntityTypes.COW).banned);
		check(!context.computeOnClient(minecraft -> worldSession.isDirty()), "saved through the Save button");
		context.runOnClient(minecraft -> worldSession.config.getOrCreateMob("minecraft:cow").banned = false);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof ConfirmScreen);
		context.takeScreenshot("cms-10-unsaved-confirm");
		context.clickScreenButton("controlmobspawns.gui.unsaved.discard");
		check(ConfigManager.active().mob(EntityTypes.COW).banned, "leaving without saving keeps the saved value");
		resetWorldConfig(server);

		CmsPayloads.Sync[] sync = new CmsPayloads.Sync[1];
		context.runOnClient(minecraft -> ControlMobSpawnsClient.setSyncListener(received -> sync[0] = received));
		context.runOnClient(minecraft -> ClientPlayNetworking.send(new CmsPayloads.RequestSync()));
		context.waitFor(minecraft -> sync[0] != null);
		ClientSession session = ClientSession.remote(ClientSession.Scope.WORLD, sync[0]);
		context.setScreen(() -> new MobDetailScreen(null, session, EntityTypes.COW));
		context.waitTicks(5);
		context.getInput().setCursorPos(400, 250);
		context.getInput().scroll(-100);
		context.waitTicks(3);
		context.takeScreenshot("cms-11-mob-detail-despawn-now");
		nbtSuggestions(context, session);
		groupScreens(context, session);
		context.setScreen(() -> null);
	}

	/** Inherited values, the Groups tab, selection mode, copy and paste. */
	private static void groupScreens(ClientGameTestContext context, ClientSession session) {
		CmsConfig.MobGroup undead = new CmsConfig.MobGroup();
		undead.name = "Undead";
		undead.members.add("#minecraft:undead");
		undead.settings.spawnRate = 300;
		undead.settings.bannedBiomes.add("#minecraft:is_ocean");
		session.config.tagGroups.add(undead);
		context.setScreen(() -> new MobDetailScreen(null, session, EntityTypes.ZOMBIE));
		context.waitTicks(3);
		context.takeScreenshot("cms-17-mob-inherited");
		context.runOnClient(minecraft -> {
			session.config.mobs.get("minecraft:zombie").forceVanilla.add("bannedBiomes:#minecraft:is_ocean");
			session.config.mobs.get("minecraft:zombie").forceVanilla.add("nbtBans:Gone");
			((MobDetailScreen) minecraft.gui.screen()).refresh();
		});
		context.waitTicks(2);
		context.takeScreenshot("cms-18-mob-inherited-forced");

		context.setScreen(() -> new MainConfigScreen(null, session));
		context.runOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).showTab(2));
		context.waitTicks(2);
		context.takeScreenshot("cms-19-groups-tab");

		context.runOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).showTab(1));
		context.clickScreenButton("controlmobspawns.gui.selection.start");
		context.getInput().typeChars("zombie");
		context.waitTicks(2);
		context.clickScreenButton("controlmobspawns.gui.selection.all");
		context.waitTicks(2);
		context.takeScreenshot("cms-20-mobs-selection");
		context.clickScreenButton("controlmobspawns.gui.edit");
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MobDetailScreen);
		context.takeScreenshot("cms-21-selection-edit");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MainConfigScreen);
		context.clickScreenButton("controlmobspawns.gui.selection.group");
		context.waitFor(minecraft -> minecraft.gui.screen() instanceof MobDetailScreen);
		check(session.config.groups.size() == 1 && session.config.groups.getFirst().members.contains("minecraft:zombie")
			&& session.config.groups.getFirst().members.contains("minecraft:zombie_villager"), "group created from the selection");

		session.config.groups.getFirst().settings.spawnRate = 42;
		context.clickScreenButton("controlmobspawns.gui.copy");
		context.runOnClient(minecraft -> MobDetailScreen.pasteInto(session.config.getOrCreateMob("minecraft:cow")));
		check(session.config.mobs.get("minecraft:cow").spawnRate == 42, "paste copies the settings");

		CmsConfig.MobSettings pig = session.config.getOrCreateMob("minecraft:pig");
		pig.banned = true;
		pig.spawnRate = 70;
		CmsConfig.MobSettings bulk = new CmsConfig.MobSettings();
		bulk.spawnRate = 250;
		bulk.disabledReasons.add("NATURAL");
		bulk.applyChangesTo(pig);
		check(pig.spawnRate == 250 && pig.banned && pig.disabledReasons.contains("NATURAL"), "editing together only writes what changed");
	}

	private static void nbtSuggestions(ClientGameTestContext context, ClientSession session) {
		String zombie = "minecraft:zombie";
		context.runOnClient(minecraft -> NbtSuggestions.request(zombie));
		context.waitFor(minecraft -> NbtSuggestions.get(zombie) != null);
		List<String> babyValues = context.computeOnClient(minecraft -> NbtSuggestions.values(zombie, "IsBaby"));
		int pathCount = context.computeOnClient(minecraft -> NbtSuggestions.paths(zombie).size());
		ControlMobSpawns.LOGGER.info("[CMS-TEST] {} NBT paths for zombies, IsBaby values {}", pathCount, babyValues);
		check(babyValues.contains("0b") && babyValues.contains("1b"), "IsBaby offered with 0b and 1b");

		CmsConfig.MobSettings settings = session.config.getOrCreateMob(zombie);
		CmsConfig.NbtBan ban = new CmsConfig.NbtBan();
		settings.nbtBans.add(ban);
		context.setScreen(() -> new NbtBansScreen(null, session, zombie, settings));
		context.runOnClient(minecraft -> focusEditBox(minecraft.gui.screen(), 0));
		context.getInput().typeChars("IsBa");
		context.waitTicks(2);
		context.takeScreenshot("cms-12-nbt-path-autocomplete");
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.waitTicks(2);
		check(ban.path.equals("IsBaby"), "Tab completes the NBT path, got " + ban.path);

		context.runOnClient(minecraft -> focusEditBox(minecraft.gui.screen(), 1));
		context.waitTicks(2);
		context.takeScreenshot("cms-13-nbt-value-suggestions");
		context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(2);
		check(babyValues.contains(ban.value), "Enter picks a suggested value, got " + ban.value);

		context.setScreen(() -> new NbtPathPickerScreen(null, session, zombie, (path, value) -> { }));
		context.waitTicks(2);
		context.takeScreenshot("cms-14-nbt-path-list");
	}

	/** Focuses the n-th text box of the rows, the way a click would. */
	private static void focusEditBox(Screen screen, int index) {
		int found = 0;
		for (GuiEventListener child : screen.children()) {
			if (child instanceof RowList list) {
				for (RowList.Row row : list.children()) {
					for (GuiEventListener widget : row.children()) {
						if (widget instanceof EditBox box && found++ == index) {
							screen.setFocused(list);
							list.setFocused(row);
							row.setFocused(box);
							return;
						}
					}
				}
			}
		}
		throw new AssertionError("[CMS-TEST] no text box " + index);
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksDownload();
				context.setScreen(() -> ModMenuIntegration.openRoot(null));
				context.waitFor(minecraft -> minecraft.gui.screen() instanceof HomeScreen home && home.canEdit() != null);
				check(!context.computeOnClient(minecraft -> ((HomeScreen) minecraft.gui.screen()).canEdit()), "non-op cannot edit on a dedicated server");
				context.takeScreenshot("cms-15-home-dedicated-not-op");

				CmsConfig denied = new CmsConfig();
				denied.getOrCreateMob("minecraft:cow").banned = true;
				String deniedJson = ConfigManager.toJsonString(denied);
				context.runOnClient(minecraft -> ClientPlayNetworking.send(new CmsPayloads.Save(false, deniedJson)));
				connection.waitForServerboundPackets();
				check(server.computeOnServer(minecraftServer -> ConfigManager.active().mob(EntityTypes.COW) == null), "non-op save rejected");

				context.setScreen(() -> null);
				context.runOnClient(minecraft -> {
					WorldConfigKey.OPEN_WORLD_CONFIG.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
					net.minecraft.client.KeyMapping.resetMapping();
				});
				context.getInput().pressKey(GLFW.GLFW_KEY_K);
				context.waitTicks(20);
				check(context.computeOnClient(minecraft -> minecraft.gui.screen() == null), "non-op: the key opens nothing");

				String name = context.computeOnClient(minecraft -> connection.getClientPlayer().getName().getString());
				server.runCommand("op " + name);
				context.setScreen(() -> null);
				context.setScreen(() -> ModMenuIntegration.openRoot(null));
				context.waitFor(minecraft -> minecraft.gui.screen() instanceof HomeScreen home && home.canEdit() != null);
				check(context.computeOnClient(minecraft -> ((HomeScreen) minecraft.gui.screen()).canEdit()), "op can edit on a dedicated server");
				context.takeScreenshot("cms-16-home-dedicated-op");
				context.setScreen(() -> null);
				context.getInput().pressKey(GLFW.GLFW_KEY_K);
				context.waitFor(minecraft -> minecraft.gui.screen() instanceof MainConfigScreen);
				check(context.computeOnClient(minecraft -> ((MainConfigScreen) minecraft.gui.screen()).session().scope == ClientSession.Scope.WORLD),
					"op: the key opens the world configuration");
				context.runOnClient(minecraft -> ClientPlayNetworking.send(new CmsPayloads.Save(true, deniedJson)));
				server.waitFor(minecraftServer -> ConfigManager.active().mob(EntityTypes.COW) != null);
				context.setScreen(() -> null);
			}
		}
	}

	private static void setWorldConfig(TestServerContext server, Consumer<CmsConfig> change) {
		server.runOnServer(minecraftServer -> {
			CmsConfig config = ConfigManager.effectiveConfig();
			change.accept(config);
			ConfigManager.saveWorld(config);
		});
	}

	private static void resetWorldConfig(TestServerContext server) {
		server.runOnServer(minecraftServer -> ConfigManager.saveWorld(ConfigManager.globalConfig()));
	}

	private static void summonAndExpect(TestServerContext server, String id, EntityType<?> type, int expected, String what) {
		server.runCommand("summon " + id + " ~ ~ ~");
		int actual = count(server, type);
		check(actual == expected, what + ": expected " + expected + " " + id + ", found " + actual);
	}

	private static int count(TestServerContext server, EntityType<?> type) {
		return server.computeOnServer(minecraftServer -> count(minecraftServer, type));
	}

	private static int count(MinecraftServer server, EntityType<?> type) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity.getType() == type && entity.isAlive()) {
					count++;
				}
			}
		}
		return count;
	}

	private static int weight(WeightedList<MobSpawnSettings.SpawnerData> list, EntityType<?> type) {
		return list.unwrap().stream().filter(entry -> entry.value().type() == type).mapToInt(Weighted::weight).sum();
	}

	private static BlockPos surface(ServerLevel level, BlockPos pos) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
	}

	private static String readWorldFile(MinecraftServer server) {
		try {
			return Files.readString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
				.resolve("serverconfig").resolve("controlmobspawns.json"));
		} catch (Exception e) {
			throw new AssertionError("cannot read the world override file", e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("[CMS-TEST] " + message);
		}
		ControlMobSpawns.LOGGER.info("[CMS-TEST] OK: {}", message);
	}
}
