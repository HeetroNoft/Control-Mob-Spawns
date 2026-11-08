package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.NbtSuggestions;
import com.heet.controlmobspawns.config.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Every NBT path of a mob, or of the members of a group, with the values seen; picking one fills
 * the path, or the path and the value. For several mobs, each path tells how many have it: a
 * setting on that path only acts on them.
 */
public class NbtPathPickerScreen extends CmsScreen {
	private static final int MAX_VALUES_SHOWN = 10;

	private final List<String> entities;
	private final BiConsumer<String, @Nullable String> onPick;

	public NbtPathPickerScreen(Screen parent, ClientSession session, List<String> entities, BiConsumer<String, @Nullable String> onPick) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_picker.title", entityName(entities)));
		this.entities = entities;
		this.onPick = onPick;
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.nbt_picker.hint");
	}

	@Override
	public void added() {
		super.added();
		NbtSuggestions.setListener(this::refresh);
		NbtSuggestions.request(entities);
	}

	@Override
	public void removed() {
		super.removed();
		NbtSuggestions.setListener(null);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		int loaded = NbtSuggestions.loaded(entities);
		if (loaded < entities.size()) {
			if (!NbtSuggestions.available()) {
				rows.add(RowList.note(Component.translatable("controlmobspawns.gui.nbt_picker.world_only")));
				return rows;
			}
			rows.add(RowList.note(entities.size() > 1
				? Component.translatable("controlmobspawns.gui.nbt_picker.loading_mobs", loaded, entities.size())
				: Component.translatable("controlmobspawns.gui.loading")));
			if (loaded == 0) {
				return rows;
			}
		}
		boolean several = entities.size() > 1;
		for (NbtSuggestions.SharedPath info : NbtSuggestions.shared(entities)) {
			if (!Mobs.contains(info.path(), search())) {
				continue;
			}
			RowList.Row row = RowList.row(Component.literal(info.path()));
			if (several) {
				Component count = Component.translatable("controlmobspawns.gui.nbt_picker.mob_count", info.mobs(), entities.size())
					.withStyle(info.mobs() == entities.size() ? ChatFormatting.WHITE : ChatFormatting.GOLD);
				row.hover(Widgets.button(count, 70, Component.translatable("controlmobspawns.gui.nbt_picker.show_mobs"),
					button -> minecraft.gui.setScreen(new NbtPathMobsScreen(this, session, info.path(), info.entities(), entities))));
			}
			rows.add(row.hover(Widgets.button(Component.translatable("controlmobspawns.gui.nbt_picker.choose_path"), 90, button -> pick(info.path(), null))));
			List<String> values = info.values();
			for (int i = 0; i < Math.min(values.size(), MAX_VALUES_SHOWN); i++) {
				String value = values.get(i);
				rows.add(RowList.row(Component.literal("    = " + value).withStyle(ChatFormatting.GRAY))
					.hover(Widgets.button(Component.translatable("controlmobspawns.gui.nbt_picker.choose_value"), 90, button -> pick(info.path(), value))));
			}
		}
		return rows;
	}

	private static Component entityName(List<String> entities) {
		if (entities.size() != 1) {
			return Component.translatable("controlmobspawns.gui.nbt_picker.mobs", entities.size());
		}
		EntityType<?> type = RuntimeConfig.entityType(entities.getFirst());
		return type == null ? Component.literal(entities.getFirst()) : Mobs.name(type);
	}

	private void pick(String path, @Nullable String value) {
		onPick.accept(path, value);
		minecraft.gui.setScreen(parent);
	}
}
