package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Base screen: a title, an optional search box, a list of rows and a footer. Text boxes can be
 * given suggestions, shown in a drop-down under the focused box.
 */
public abstract class CmsScreen extends Screen {
	private static final int MAX_VISIBLE_SUGGESTIONS = 8;
	private static final int SUGGESTION_HEIGHT = 12;

	protected final Screen parent;
	protected final ClientSession session;
	protected HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	protected RowList list;
	protected @Nullable EditBox searchBox;
	private String search = "";

	private final Map<EditBox, Supplier<List<String>>> completions = new IdentityHashMap<>();
	private @Nullable EditBox suggestionBox;
	private List<String> suggestions = List.of();
	private int selectedSuggestion;
	private int firstVisibleSuggestion;
	private @Nullable String dismissedFor;
	private int popupX;
	private int popupY;
	private int popupWidth;

	protected CmsScreen(Screen parent, ClientSession session, Component title) {
		super(title);
		this.parent = parent;
		this.session = session;
	}

	/** Whether the header shows a search box filtering the rows. */
	protected boolean hasSearch() {
		return false;
	}

	/** A short explanation of the screen, shown on the ⓘ next to the title. */
	protected @Nullable Component help() {
		return null;
	}

	/** Tab names; a tab bar is shown when there are several. */
	protected List<Component> tabs() {
		return List.of();
	}

	protected int selectedTab() {
		return 0;
	}

	protected void onTabSelected(int tab) {
	}

	/** Widgets above the list, spanning the row width; null for no toolbar. */
	protected @Nullable LayoutElement toolbar(int rowWidth) {
		return null;
	}

	protected abstract List<RowList.Row> buildRows();

	/** Buttons added to the footer, before "Done" when it is shown. */
	protected void addFooterButtons(LinearLayout footer) {
	}

	protected boolean showDoneButton() {
		return true;
	}

	/** Called when the screen is closed with "Done" or Escape. */
	protected void onDone() {
	}

	protected String search() {
		return search;
	}

	/** Offers {@code candidates} in a drop-down while {@code box} is focused. */
	protected EditBox autocomplete(EditBox box, Supplier<List<String>> candidates) {
		completions.put(box, candidates);
		return box;
	}

	/** Switches tab: the header changes, so the widgets are rebuilt. */
	protected void selectTab(int tab) {
		search = "";
		onTabSelected(tab);
		rebuildWidgets();
		refresh();
	}

	@Override
	protected void init() {
		layout = new HeaderAndFooterLayout(this);
		int rowWidth = RowList.rowWidth(width);
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		LinearLayout titleLine = header.addChild(LinearLayout.horizontal().spacing(4));
		titleLine.addChild(new StringWidget(title, font));
		Component help = help();
		if (help != null) {
			StringWidget info = new StringWidget(Component.literal("ⓘ").withStyle(ChatFormatting.GRAY), font);
			info.setTooltip(Tooltip.create(help));
			titleLine.addChild(info);
		}
		int headerHeight = 24;
		List<Component> tabs = tabs();
		if (tabs.size() > 1) {
			LinearLayout tabBar = header.addChild(LinearLayout.horizontal().spacing(2));
			for (int i = 0; i < tabs.size(); i++) {
				int index = i;
				tabBar.addChild(new TabButton(tabs.get(i), i == selectedTab(), () -> selectTab(index)));
			}
			headerHeight += 20;
		}
		searchBox = null;
		if (hasSearch()) {
			searchBox = new EditBox(font, rowWidth, 18, Component.translatable("controlmobspawns.gui.search"));
			searchBox.setHint(Component.translatable("controlmobspawns.gui.search").withStyle(ChatFormatting.DARK_GRAY));
			searchBox.setValue(search);
			searchBox.setResponder(value -> {
				search = value;
				refresh();
			});
			header.addChild(searchBox);
			headerHeight += 22;
		}
		LayoutElement toolbar = toolbar(rowWidth);
		if (toolbar != null) {
			header.addChild(toolbar);
			headerHeight += 24;
		}
		layout.setHeaderHeight(headerHeight);
		list = layout.addToContents(new RowList(minecraft, width));
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		addFooterButtons(footer);
		if (showDoneButton()) {
			footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(150).build());
		}
		layout.visitWidgets(this::addRenderableWidget);
		refresh();
		repositionElements();
	}

	/** A toolbar line: a label on the left and widgets on the right, as wide as the rows. */
	protected LayoutElement toolbarLine(int rowWidth, Component label, AbstractWidget... right) {
		LinearLayout line = LinearLayout.horizontal().spacing(4);
		line.defaultCellSetting().alignVerticallyMiddle();
		int rightWidth = 0;
		for (AbstractWidget widget : right) {
			rightWidth += widget.getWidth() + 4;
		}
		line.addChild(new StringWidget(Math.max(0, rowWidth - rightWidth), 20, label, font));
		for (AbstractWidget widget : right) {
			line.addChild(widget);
		}
		return line;
	}

	@Override
	protected void setInitialFocus() {
		if (searchBox != null) {
			setInitialFocus(searchBox);
		} else {
			super.setInitialFocus();
		}
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		if (list != null) {
			list.updateSize(width, layout);
		}
	}

	/** Called each time the screen is shown, including when coming back from a sub-screen. */
	@Override
	public void added() {
		super.added();
		refresh();
	}

	/** Rebuilds the rows, keeping the scroll position. */
	public void refresh() {
		if (list != null) {
			completions.clear();
			list.setRows(buildRows());
		}
	}

	@Override
	public void onClose() {
		onDone();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		updateSuggestions();
		if (suggestionBox == null) {
			return;
		}
		int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
		popupWidth = suggestionBox.getWidth();
		for (int i = 0; i < visible; i++) {
			popupWidth = Math.max(popupWidth, font.width(suggestions.get(firstVisibleSuggestion + i)) + 6);
		}
		popupX = Math.min(suggestionBox.getX(), width - popupWidth - 2);
		int height = visible * SUGGESTION_HEIGHT;
		popupY = suggestionBox.getY() + suggestionBox.getHeight() + 1;
		if (popupY + height > this.height) {
			popupY = suggestionBox.getY() - height - 1;
		}
		graphics.nextStratum();
		graphics.fill(popupX, popupY, popupX + popupWidth, popupY + height, 0xF0101010);
		graphics.outline(popupX - 1, popupY - 1, popupWidth + 2, height + 2, 0xFF606060);
		for (int i = 0; i < visible; i++) {
			int index = firstVisibleSuggestion + i;
			int y = popupY + i * SUGGESTION_HEIGHT;
			boolean selected = index == selectedSuggestion;
			if (selected) {
				graphics.fill(popupX, y, popupX + popupWidth, y + SUGGESTION_HEIGHT, 0xFF303060);
			}
			graphics.text(font, suggestions.get(index), popupX + 3, y + 2, selected ? 0xFFFFFF55 : 0xFFE0E0E0);
		}
		if (suggestions.size() > visible) {
			String more = "+" + (suggestions.size() - visible - firstVisibleSuggestion);
			graphics.text(font, more, popupX + popupWidth - font.width(more) - 3, popupY + height - SUGGESTION_HEIGHT + 2, 0xFF808080);
		}
	}

	/** Finds the focused box with suggestions and filters them by its text. */
	private void updateSuggestions() {
		EditBox focused = null;
		for (EditBox box : completions.keySet()) {
			if (box.isFocused() && box.isVisible()) {
				focused = box;
			}
		}
		if (focused == null || focused.getValue().equals(dismissedFor)) {
			hideSuggestions();
			return;
		}
		dismissedFor = null;
		String text = focused.getValue().toLowerCase(Locale.ROOT);
		List<String> matching = new ArrayList<>();
		for (String candidate : completions.get(focused).get()) {
			String lower = candidate.toLowerCase(Locale.ROOT);
			if (!lower.equals(text) && lower.contains(text)) {
				matching.add(candidate);
			}
		}
		if (matching.isEmpty()) {
			hideSuggestions();
			return;
		}
		if (focused != suggestionBox || !matching.equals(suggestions)) {
			selectedSuggestion = 0;
			firstVisibleSuggestion = 0;
		}
		suggestionBox = focused;
		suggestions = matching;
	}

	private void hideSuggestions() {
		suggestionBox = null;
		suggestions = List.of();
	}

	private void acceptSuggestion(int index) {
		if (suggestionBox != null && index >= 0 && index < suggestions.size()) {
			String value = suggestions.get(index);
			suggestionBox.setValue(value);
			dismissedFor = value;
			hideSuggestions();
		}
	}

	private void moveSelection(int delta) {
		int size = suggestions.size();
		selectedSuggestion = Math.floorMod(selectedSuggestion + delta, size);
		if (selectedSuggestion < firstVisibleSuggestion) {
			firstVisibleSuggestion = selectedSuggestion;
		} else if (selectedSuggestion >= firstVisibleSuggestion + MAX_VISIBLE_SUGGESTIONS) {
			firstVisibleSuggestion = selectedSuggestion - MAX_VISIBLE_SUGGESTIONS + 1;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (suggestionBox != null && !suggestions.isEmpty()) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_DOWN -> {
					moveSelection(1);
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					moveSelection(-1);
					return true;
				}
				case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					acceptSuggestion(selectedSuggestion);
					return true;
				}
				case GLFW.GLFW_KEY_ESCAPE -> {
					dismissedFor = suggestionBox.getValue();
					hideSuggestions();
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (suggestionBox != null && !suggestions.isEmpty()) {
			int visible = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
			if (event.x() >= popupX && event.x() < popupX + popupWidth
				&& event.y() >= popupY && event.y() < popupY + visible * SUGGESTION_HEIGHT) {
				acceptSuggestion(firstVisibleSuggestion + (int) ((event.y() - popupY) / SUGGESTION_HEIGHT));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (suggestionBox != null && suggestions.size() > MAX_VISIBLE_SUGGESTIONS
			&& x >= popupX && x < popupX + popupWidth && y >= popupY && y < popupY + MAX_VISIBLE_SUGGESTIONS * SUGGESTION_HEIGHT) {
			moveSelection(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
