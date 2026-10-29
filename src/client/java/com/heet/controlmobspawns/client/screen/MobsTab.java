package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import com.heet.controlmobspawns.config.MobSettingsResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Every mob; at rest only what differs from vanilla, the controls on hover. In selection mode,
 * several mobs can be edited, pasted into or grouped at once.
 */
class MobsTab implements MainConfigScreen.Tab {
	private boolean selecting;
	private final Set<String> selected = new LinkedHashSet<>();

	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.gui.mobs.title");
	}

	@Override
	public LayoutElement toolbar(MainConfigScreen screen, int rowWidth) {
		if (!selecting) {
			return screen.toolbarLine(rowWidth, Component.empty(),
				Widgets.button(Component.translatable("controlmobspawns.gui.selection.start"), 90, Component.translatable("controlmobspawns.gui.selection.start.hint"),
					button -> {
						selecting = true;
						screen.reinit();
					}));
		}
		Button all = Widgets.button(Component.translatable("controlmobspawns.gui.selection.all"), 60, Component.translatable("controlmobspawns.gui.selection.all.hint"),
			button -> {
				Mobs.all().stream().filter(type -> Mobs.matches(type, screen.search())).map(Mobs::id).forEach(selected::add);
				screen.reinit();
			});
		Button edit = Widgets.button(Component.translatable("controlmobspawns.gui.edit"), 50,
			button -> screen.open(MobDetailScreen.forSelection(screen, screen.session(), List.copyOf(selected), screen::refresh)));
		Button paste = Widgets.button(Component.translatable("controlmobspawns.gui.paste"), 50, Component.translatable("controlmobspawns.gui.selection.paste.hint"),
			button -> {
				selected.forEach(id -> MobDetailScreen.pasteInto(screen.session().config.getOrCreateMob(id)));
				screen.refresh();
			});
		Button group = Widgets.button(Component.translatable("controlmobspawns.gui.selection.group"), 60, Component.translatable("controlmobspawns.gui.selection.group.hint"),
			button -> {
				List<CmsConfig.MobGroup> groups = screen.session().config.groups;
				CmsConfig.MobGroup created = new CmsConfig.MobGroup();
				created.name = Component.translatable("controlmobspawns.gui.groups.default_name", groups.size() + 1).getString();
				created.members.addAll(selected);
				groups.add(created);
				screen.open(MobDetailScreen.forGroup(screen, screen.session(), created));
			});
		Button done = Widgets.button(Component.literal("✕"), 20, Component.translatable("controlmobspawns.gui.selection.stop"), button -> {
			selecting = false;
			selected.clear();
			screen.reinit();
		});
		edit.active = !selected.isEmpty();
		paste.active = !selected.isEmpty() && MobDetailScreen.hasClipboard();
		group.active = !selected.isEmpty();
		return screen.toolbarLine(rowWidth, Component.translatable("controlmobspawns.gui.selection.count", selected.size()).withStyle(ChatFormatting.GRAY),
			all, edit, paste, group, done);
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		List<RowList.Row> rows = new ArrayList<>();
		for (EntityType<?> type : Mobs.all()) {
			if (Mobs.matches(type, search)) {
				rows.add(mobRow(screen, type));
			}
		}
		return rows;
	}

	private RowList.Row mobRow(MainConfigScreen screen, EntityType<?> type) {
		String id = Mobs.id(type);
		CmsConfig config = screen.session().config;
		CmsConfig.MobSettings existing = config.mobs.get(id);
		CmsConfig.@Nullable MobSettings effective = MobSettingsResolver.resolve(config, type);
		double rate = existing == null ? 100.0 : existing.spawnRate;
		boolean banned = existing != null && existing.banned;
		RowList.Row row = RowList.row(Mobs.label(type, effective != null && effective.banned))
			.icon(Mobs.icon(type))
			.rest(summary(existing, effective, !MobSettingsResolver.groupSources(config, type).isEmpty()));
		if (selecting) {
			row.add(Widgets.checkbox(selected.contains(id), value -> {
				if (value) {
					selected.add(id);
				} else {
					selected.remove(id);
				}
				screen.reinit();
			}));
		}
		return row
			.hover(Widgets.positiveDoubleBox(rate, 45, Component.translatable("controlmobspawns.gui.spawn_rate"),
				value -> config.getOrCreateMob(id).spawnRate = value))
			.hover(Widgets.toggle(!banned, 70, Component.translatable("controlmobspawns.gui.allowed"), Component.translatable("controlmobspawns.gui.banned"),
				allowed -> {
					config.getOrCreateMob(id).banned = !allowed;
					screen.refresh();
				}))
			.hover(Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.mob.settings"),
				button -> screen.open(new MobDetailScreen(screen, screen.session(), type))));
	}

	/**
	 * What differs from vanilla, besides the ban shown by the red name: "250 % · settings ·
	 * groups". The rate shown is the one that applies, inherited or not.
	 */
	private static Component summary(CmsConfig.@Nullable MobSettings own, CmsConfig.@Nullable MobSettings effective, boolean inGroups) {
		MutableComponent text = Component.empty().withStyle(ChatFormatting.GRAY);
		if (effective != null && effective.spawnRate != 100.0) {
			text.append(Component.translatable("controlmobspawns.gui.percent", Widgets.formatNumber(effective.spawnRate)));
		}
		if (own != null && own.hasDetailedSettings()) {
			separate(text);
			text.append(Component.translatable("controlmobspawns.gui.mobs.other_settings"));
		}
		if (inGroups) {
			separate(text);
			text.append(Component.translatable("controlmobspawns.gui.mobs.in_groups").withStyle(ChatFormatting.AQUA));
		}
		return text;
	}

	private static void separate(MutableComponent text) {
		if (!text.getSiblings().isEmpty()) {
			text.append(" · ");
		}
	}
}
