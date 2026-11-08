package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The mobs of a group that have an NBT path, then those that do not: a setting on the path skips the latter. */
public class NbtPathMobsScreen extends CmsScreen {
	private final List<String> having;
	private final List<String> all;

	public NbtPathMobsScreen(Screen parent, ClientSession session, String path, List<String> having, List<String> all) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_mobs.title", path));
		this.having = having;
		this.all = all;
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.nbt_mobs.having", having.size())));
		for (String id : having) {
			add(rows, id);
		}
		List<String> missing = all.stream().filter(id -> !having.contains(id)).toList();
		if (!missing.isEmpty()) {
			rows.add(RowList.header(Component.translatable("controlmobspawns.gui.nbt_mobs.missing", missing.size())));
			missing.forEach(id -> add(rows, id));
		}
		return rows;
	}

	private void add(List<RowList.Row> rows, String id) {
		if (GroupMembersScreen.matchesSearch(id, search())) {
			rows.add(GroupMembersScreen.entryRow(id, false));
		}
	}
}
