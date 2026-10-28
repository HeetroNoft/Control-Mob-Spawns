package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Edits a list of ids. The search box filters the suggestions and can also add an id that is
 * not suggested, such as a tag written as #namespace:path.
 */
public class IdListScreen extends CmsScreen {
	private static final int MAX_SUGGESTIONS = 200;

	private final List<String> target;
	private final List<String> suggestions;

	public IdListScreen(Screen parent, ClientSession session, Component title, List<String> target, List<String> suggestions) {
		super(parent, session, title);
		this.target = target;
		this.suggestions = suggestions;
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.ids.help");
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.ids.selected", target.size())));
		if (target.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.ids.none_selected")));
		}
		for (String entry : List.copyOf(target)) {
			rows.add(RowList.row(Component.literal(entry))
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					target.remove(entry);
					refresh();
				})));
		}

		String typed = search().trim();
		if (isValidId(typed) && !target.contains(typed) && !suggestions.contains(typed)) {
			rows.add(RowList.row(Component.translatable("controlmobspawns.gui.ids.add_typed", typed).withStyle(ChatFormatting.GREEN))
				.add(Widgets.button(Component.literal("+"), 20, button -> add(typed))));
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.ids.suggestions")));
		int shown = 0;
		for (String suggestion : suggestions) {
			if (shown >= MAX_SUGGESTIONS) {
				break;
			}
			if (!target.contains(suggestion) && Mobs.contains(suggestion, search())) {
				rows.add(RowList.row(Component.literal(suggestion).withStyle(suggestion.startsWith("#") ? ChatFormatting.GOLD : ChatFormatting.WHITE))
					.hover(Widgets.button(Component.literal("+"), 20, Component.translatable("controlmobspawns.gui.add"), button -> add(suggestion))));
				shown++;
			}
		}
		if (suggestions.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.ids.no_suggestions")));
		}
		return rows;
	}

	private void add(String id) {
		target.add(id);
		refresh();
	}

	private static boolean isValidId(String text) {
		String id = text.startsWith("#") ? text.substring(1) : text;
		return !id.isEmpty() && id.contains(":") && Identifier.tryParse(id) != null;
	}
}
