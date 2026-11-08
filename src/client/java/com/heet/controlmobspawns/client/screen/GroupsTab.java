package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;

/**
 * Groups of mobs, in priority order. A group holds mobs, entity tags and mob categories, minus
 * its exclusions. A mob's own settings come first, then its groups.
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
			}));
	}

	@Override
	public List<RowList.Row> rows(MainConfigScreen screen, String search) {
		CmsConfig config = screen.session().config;
		List<RowList.Row> rows = new ArrayList<>();
		if (config.groups.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.groups.none")));
		}
		for (CmsConfig.MobGroup group : List.copyOf(config.groups)) {
			if (matches(group, search)) {
				rows.add(groupRow(screen, config.groups, group));
			}
		}
		return rows;
	}

	private static boolean matches(CmsConfig.MobGroup group, String search) {
		return Mobs.contains(group.name, search) || group.members.stream().anyMatch(member -> Mobs.contains(member, search));
	}

	private static RowList.Row groupRow(MainConfigScreen screen, List<CmsConfig.MobGroup> list, CmsConfig.MobGroup group) {
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
		Component members = Component.translatable("controlmobspawns.gui.groups.members", group.members.size());
		return RowList.row(Component.literal("#" + (index + 1)).withStyle(ChatFormatting.GRAY))
			.add(name)
			.add(Widgets.button(members, 80, button -> screen.open(new GroupMembersScreen(screen, screen.session(), members, group.members))))
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
