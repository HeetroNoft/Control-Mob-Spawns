package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Blocks the mob when one of its NBT values matches. */
public class NbtBansScreen extends CmsScreen {
	private final CmsConfig.MobSettings settings;

	public NbtBansScreen(Screen parent, ClientSession session, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_bans.title"));
		this.settings = settings;
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.help").withStyle(ChatFormatting.GRAY), null,
			Component.translatable("controlmobspawns.gui.nbt_bans.hint")));
		rows.add(RowList.row(Component.translatable("controlmobspawns.gui.nbt_bans.columns").withStyle(ChatFormatting.GRAY)));
		for (CmsConfig.NbtBan ban : List.copyOf(settings.nbtBans)) {
			rows.add(RowList.row(Component.empty(),
				Widgets.textBox(ban.path, 150, Component.translatable("controlmobspawns.gui.nbt.path"), NbtValidation::isPath, value -> ban.path = value),
				Widgets.textBox(ban.value, 110, Component.translatable("controlmobspawns.gui.nbt.value"), NbtValidation::isValue, value -> ban.value = value),
				Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					settings.nbtBans.remove(ban);
					refresh();
				})));
		}
		return rows;
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		footer.addChild(Widgets.button(Component.translatable("controlmobspawns.gui.add"), 100, button -> {
			settings.nbtBans.add(new CmsConfig.NbtBan());
			refresh();
		}));
	}
}
