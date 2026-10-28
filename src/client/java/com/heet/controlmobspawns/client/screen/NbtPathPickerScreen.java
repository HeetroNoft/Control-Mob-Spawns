package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.NbtSuggestions;
import com.heet.controlmobspawns.config.RuntimeConfig;
import com.heet.controlmobspawns.network.CmsPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** Every NBT path of a mob with the values seen; picking one fills the path, or the path and the value. */
public class NbtPathPickerScreen extends CmsScreen {
	private static final int MAX_VALUES_SHOWN = 10;

	private final String entity;
	private final BiConsumer<String, @Nullable String> onPick;

	public NbtPathPickerScreen(Screen parent, ClientSession session, String entity, BiConsumer<String, @Nullable String> onPick) {
		super(parent, session, Component.translatable("controlmobspawns.gui.nbt_picker.title", entityName(entity)));
		this.entity = entity;
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
		NbtSuggestions.request(entity);
	}

	@Override
	public void removed() {
		super.removed();
		NbtSuggestions.setListener(null);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		List<CmsPayloads.NbtPathInfo> paths = NbtSuggestions.get(entity);
		if (paths == null) {
			rows.add(RowList.note(Component.translatable(NbtSuggestions.available()
				? "controlmobspawns.gui.loading" : "controlmobspawns.gui.nbt_picker.world_only")));
			return rows;
		}
		for (CmsPayloads.NbtPathInfo info : paths) {
			if (!Mobs.contains(info.path(), search())) {
				continue;
			}
			rows.add(RowList.row(Component.literal(info.path()))
				.hover(Widgets.button(Component.translatable("controlmobspawns.gui.nbt_picker.choose_path"), 90, button -> pick(info.path(), null))));
			List<String> values = info.values();
			for (int i = 0; i < Math.min(values.size(), MAX_VALUES_SHOWN); i++) {
				String value = values.get(i);
				rows.add(RowList.row(Component.literal("    = " + value).withStyle(ChatFormatting.GRAY))
					.hover(Widgets.button(Component.translatable("controlmobspawns.gui.nbt_picker.choose_value"), 90, button -> pick(info.path(), value))));
			}
		}
		return rows;
	}

	private static Component entityName(String entity) {
		EntityType<?> type = RuntimeConfig.entityType(entity);
		return type == null ? Component.literal(entity) : Mobs.name(type);
	}

	private void pick(String path, @Nullable String value) {
		onPick.accept(path, value);
		minecraft.gui.setScreen(parent);
	}
}
