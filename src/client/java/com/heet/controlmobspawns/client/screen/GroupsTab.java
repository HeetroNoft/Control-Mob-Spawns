package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;

/**
 * Groups of mobs, then settings for entity tags and categories, each list in priority order. A
 * mob's own settings come first, then its groups, then its tags.
 */
class GroupsTab implements MainConfigScreen.Tab {
	@Override
	public Component title() {
		return Component.translatable("controlmobspawns.gui.groups.title");
	}

	@Override
	public LayoutElement toolbar(MainConfigScreen screen, int rowWidth) {
		CmsConfig config = screen.session().config;
		return screen.toolbarLine(rowWidth, Component.empty(),
			Widgets.info(Component.translatable("controlmobspawns.gui.groups.hint")),
			Widgets.button(Component.translatable("controlmobspawns.gui.groups.new_group"), 90, button -> {
				CmsConfig.MobGroup group = new CmsConfig.MobGroup();
				group.name = Component.translatable("controlmobspawns.gui.groups.default_name", config.groups.size() + 1).getString();
				config.groups.add(group);
				screen.refresh();
			}),
			Widgets.button(Component.translatable("controlmobspawns.gui.groups.new_tag"), 90, button -> {
				config.tagGroups.add(new CmsConfig.MobGroup());
				screen.refresh();
			}));
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		CmsConfig config = screen.session().config;
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.groups.groups")).info(Component.translatable("controlmobspawns.gui.groups.groups.hint")));
		if (config.groups.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.groups.none")));
		}
		List<String> mobIds = Mobs.all().stream().map(Mobs::id).toList();
		for (CmsConfig.MobGroup group : List.copyOf(config.groups)) {
			if (matches(group, search)) {
				rows.add(groupRow(screen, config.groups, group, "controlmobspawns.gui.groups.mobs", mobIds));
			}
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.groups.tags")).info(Component.translatable("controlmobspawns.gui.groups.tags.hint")));
		if (config.tagGroups.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.groups.none")));
		}
		for (CmsConfig.MobGroup group : List.copyOf(config.tagGroups)) {
			if (matches(group, search)) {
				rows.add(groupRow(screen, config.tagGroups, group, "controlmobspawns.gui.groups.tags_button", tagChoices()));
			}
		}
		return rows;
	}

	/** Entity tags (known once in a world) and mob categories. */
	private static List<String> tagChoices() {
		List<String> tags = BuiltInRegistries.ENTITY_TYPE.getTags().map(tag -> "#" + tag.key().location()).sorted().toList();
		Stream<String> categories = Stream.of(MobCategory.values()).filter(category -> category != MobCategory.MISC).map(category -> "@" + category.getName());
		return Stream.concat(categories, tags.stream()).toList();
	}

	private static boolean matches(CmsConfig.MobGroup group, String search) {
		return Mobs.contains(group.name, search) || group.members.stream().anyMatch(member -> Mobs.contains(member, search));
	}

	private static RowList.Row groupRow(MainConfigScreen screen, List<CmsConfig.MobGroup> list, CmsConfig.MobGroup group, String membersKey,
		List<String> memberChoices) {
		int index = list.indexOf(group);
		var name = Widgets.textBox(group.name, 130, Component.translatable("controlmobspawns.gui.groups.name"), value -> group.name = value);
		name.setHint(Component.translatable("controlmobspawns.gui.groups.name").withStyle(ChatFormatting.DARK_GRAY));
		var up = Widgets.button(Component.literal("↑"), 20, Component.translatable("controlmobspawns.gui.groups.up"), button -> {
			list.remove(group);
			list.add(index - 1, group);
			screen.refresh();
		});
		up.active = index > 0;
		var down = Widgets.button(Component.literal("↓"), 20, Component.translatable("controlmobspawns.gui.groups.down"), button -> {
			list.remove(group);
			list.add(index + 1, group);
			screen.refresh();
		});
		down.active = index < list.size() - 1;
		return RowList.row(Component.literal("#" + (index + 1)).withStyle(ChatFormatting.GRAY))
			.add(name)
			.add(Widgets.button(Component.translatable(membersKey, group.members.size()), 80,
				button -> screen.open(new IdListScreen(screen, screen.session(), Component.translatable(membersKey, group.members.size()), group.members, memberChoices))))
			.add(Widgets.button(Component.literal("⚙"), 20, Component.translatable("controlmobspawns.gui.groups.settings"),
				button -> screen.open(MobDetailScreen.forGroup(screen, screen.session(), group))))
			.add(up)
			.add(down)
			.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
				list.remove(group);
				screen.refresh();
			}))
			.keepHover();
	}
}
