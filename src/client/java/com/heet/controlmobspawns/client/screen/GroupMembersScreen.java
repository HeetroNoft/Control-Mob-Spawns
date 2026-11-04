package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.config.MobSettingsResolver;
import com.heet.controlmobspawns.config.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

/**
 * The members of a group: mobs, entity tags and mob categories. What the selected entries already
 * cover can be excluded with "-": "@monster" without zombies, or without "#minecraft:undead".
 */
public class GroupMembersScreen extends CmsScreen {
	private static final int MAX_SUGGESTIONS = 200;

	private final List<String> members;

	public GroupMembersScreen(Screen parent, ClientSession session, Component title, List<String> members) {
		super(parent, session, title);
		this.members = members;
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected @Nullable Component help() {
		return Component.translatable("controlmobspawns.gui.members.help");
	}

	@Override
	protected List<RowList.Row> buildRows() {
		List<RowList.Row> rows = new ArrayList<>();
		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.ids.selected", members.size())));
		if (members.isEmpty()) {
			rows.add(RowList.note(Component.translatable("controlmobspawns.gui.ids.none_selected")));
		}
		for (String member : List.copyOf(members)) {
			boolean excluded = member.startsWith(MobSettingsResolver.EXCLUDE);
			String entry = excluded ? member.substring(MobSettingsResolver.EXCLUDE.length()) : member;
			rows.add(entryRow(entry, excluded)
				.hover(Widgets.button(Component.literal("✕").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.remove"), button -> {
					members.remove(member);
					refresh();
				})));
		}

		String typed = search().trim();
		if (isValidEntry(typed) && !members.contains(typed) && suggestions().noneMatch(typed::equals)) {
			rows.add(RowList.row(Component.translatable("controlmobspawns.gui.ids.add_typed", typed).withStyle(ChatFormatting.GREEN))
				.add(Widgets.button(Component.literal("+"), 20, button -> add(typed))));
		}

		rows.add(RowList.header(Component.translatable("controlmobspawns.gui.ids.suggestions")));
		List<String> includes = members.stream().filter(member -> !member.startsWith(MobSettingsResolver.EXCLUDE)).toList();
		int shown = 0;
		for (String suggestion : suggestions().toList()) {
			if (shown >= MAX_SUGGESTIONS) {
				break;
			}
			if (members.contains(suggestion) || members.contains(MobSettingsResolver.EXCLUDE + suggestion) || !matchesSearch(suggestion, search())) {
				continue;
			}
			RowList.Row row = entryRow(suggestion, false);
			Coverage coverage = coverage(suggestion, includes);
			if (coverage != Coverage.ALL) {
				row.hover(Widgets.button(Component.literal("+"), 20, Component.translatable("controlmobspawns.gui.add"), button -> add(suggestion)));
			}
			if (coverage != Coverage.NONE) {
				row.hover(Widgets.button(Component.literal("-").withStyle(ChatFormatting.RED), 20, Component.translatable("controlmobspawns.gui.members.exclude"),
					button -> add(MobSettingsResolver.EXCLUDE + suggestion)));
			}
			rows.add(row);
			shown++;
		}
		return rows;
	}

	private enum Coverage {
		NONE, SOME, ALL
	}

	/** How many of the mobs an entry matches are already in the group through the selected entries. */
	private static Coverage coverage(String entry, List<String> includes) {
		int matched = 0;
		int covered = 0;
		for (EntityType<?> type : Mobs.all()) {
			if (MobSettingsResolver.matches(entry, type)) {
				matched++;
				if (includes.stream().anyMatch(include -> MobSettingsResolver.matches(include, type))) {
					covered++;
				}
			}
		}
		return covered == 0 ? Coverage.NONE : covered == matched ? Coverage.ALL : Coverage.SOME;
	}

	/** Categories, then entity tags (known once in a world), then mobs. */
	private static Stream<String> suggestions() {
		Stream<String> categories = Stream.of(MobCategory.values()).filter(category -> category != MobCategory.MISC).map(category -> "@" + category.getName());
		Stream<String> tags = BuiltInRegistries.ENTITY_TYPE.getTags().map(tag -> "#" + tag.key().location()).sorted();
		return Stream.of(categories, tags, Mobs.all().stream().map(Mobs::id)).flatMap(stream -> stream);
	}

	/** A mob with its egg, name and gray id; a tag in gold; a category in green. Excluded ones are red. */
	static RowList.Row entryRow(String entry, boolean excluded) {
		EntityType<?> type = entry.startsWith("#") || entry.startsWith("@") ? null : RuntimeConfig.entityType(entry);
		MutableComponent label = Component.empty();
		if (excluded) {
			label.append(Component.translatable("controlmobspawns.gui.members.except").withStyle(ChatFormatting.RED)).append(" ");
		}
		if (type == null) {
			label.append(Component.literal(entry).withStyle(entry.startsWith("#") ? ChatFormatting.GOLD : entry.startsWith("@") ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			return RowList.row(label);
		}
		label.append(Mobs.name(type)).append(Component.literal("  " + entry).withStyle(ChatFormatting.DARK_GRAY));
		return RowList.row(label).icon(Mobs.icon(type));
	}

	/** Whether an entry matches the search by its id or, for a mob, by its name. */
	static boolean matchesSearch(String entry, String search) {
		if (Mobs.contains(entry, search)) {
			return true;
		}
		EntityType<?> type = entry.startsWith("#") || entry.startsWith("@") ? null : RuntimeConfig.entityType(entry);
		return type != null && Mobs.matches(type, search);
	}

	private void add(String entry) {
		members.add(entry);
		refresh();
	}

	private static boolean isValidEntry(String text) {
		if (text.startsWith("@")) {
			return Stream.of(MobCategory.values()).anyMatch(category -> category.getName().equals(text.substring(1)));
		}
		String id = text.startsWith("#") ? text.substring(1) : text;
		return !id.isEmpty() && id.contains(":") && Identifier.tryParse(id) != null;
	}
}
