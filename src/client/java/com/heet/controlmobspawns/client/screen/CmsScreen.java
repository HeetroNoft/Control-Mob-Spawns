package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Base screen: a title, an optional search box, a list of rows and a footer. */
public abstract class CmsScreen extends Screen {
	protected final Screen parent;
	protected final ClientSession session;
	protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	protected RowList list;
	protected @Nullable EditBox searchBox;
	private String search = "";

	protected CmsScreen(Screen parent, ClientSession session, Component title) {
		super(title);
		this.parent = parent;
		this.session = session;
	}

	/** Whether the header shows a search box filtering the rows. */
	protected boolean hasSearch() {
		return false;
	}

	protected abstract List<RowList.Row> buildRows();

	/** Buttons added to the footer before "Done". */
	protected void addFooterButtons(LinearLayout footer) {
	}

	/** Called when the screen is closed. Top-level screens save the session here. */
	protected void onDone() {
	}

	protected String search() {
		return search;
	}

	@Override
	protected void init() {
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		if (hasSearch()) {
			searchBox = new EditBox(font, 220, 18, Component.translatable("controlmobspawns.gui.search"));
			searchBox.setHint(Component.translatable("controlmobspawns.gui.search"));
			searchBox.setValue(search);
			searchBox.setResponder(value -> {
				search = value;
				refresh();
			});
			header.addChild(searchBox);
			layout.setHeaderHeight(52);
		}
		list = layout.addToContents(new RowList(minecraft, width));
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		addFooterButtons(footer);
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(150).build());
		layout.visitWidgets(this::addRenderableWidget);
		refresh();
		repositionElements();
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
			list.setRows(buildRows());
		}
	}

	@Override
	public void onClose() {
		onDone();
		minecraft.gui.setScreen(parent);
	}
}
