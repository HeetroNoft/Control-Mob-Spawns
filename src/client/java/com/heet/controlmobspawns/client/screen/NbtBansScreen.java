package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.NbtSuggestions;
import com.heet.controlmobspawns.config.CmsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Blocks the mob when one of its NBT values matches. */
public class NbtBansScreen extends CmsScreen {
	/** The mobs whose NBT paths are suggested: one mob, or every member of a group. */
	private final List<String> entities;
	private final CmsConfig.MobSettings settings;

	public NbtBansScreen(Screen parent, ClientSession session, List<String> entities, CmsConfig.MobSettings settings) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_bans.title"));
		this.entities = entities;
		this.settings = settings;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.nbt_bans.hint");
	}

	@Override
	public void added() {
		super.added();
		NbtSuggestions.request(entities);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		if (settings.nbtBans.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.nbt.empty")));
		}
		for (CmsConfig.NbtBan ban : List.copyOf(settings.nbtBans)) {
			rows.add(RowList.row(Component.empty())
				.add(NbtValidation.pathBox(this, entities, ban.path, 150, value -> ban.path = value))
				.hover(NbtValidation.pickerButton(this, session, entities, (path, value) -> {
					ban.path = path;
					if (value != null) {
						ban.value = value;
					}
				}))
				.add(NbtValidation.valueBox(this, entities, ban.value, 110, () -> ban.path, value -> ban.value = value))
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
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
