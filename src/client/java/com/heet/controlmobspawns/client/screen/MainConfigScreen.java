package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The configuration screen: tabs, a search box fixed above the list, and Cancel / Save. Changes
 * stay pending until saved; sub-screens edit the same pending configuration.
 */
public class MainConfigScreen extends CmsScreen {
	/** A tab of this screen. */
	interface Tab {
		Component title();

		List<RowList.Row> rows(MainConfigScreen screen, String search);

		default @Nullable LayoutElement toolbar(MainConfigScreen screen, int rowWidth) {
			return null;
		}

		default void onShow(MainConfigScreen screen) {
		}

		default void onHide() {
		}
	}

	private final List<Tab> tabs = new ArrayList<>();
	private int tab;
	private @Nullable Button cancelButton;
	private @Nullable Button saveButton;

	public MainConfigScreen(Screen parent, ClientSession session) {
		super(parent, session, Component.translatable("controlmobspawns.title").append(" — ")
			.append(Component.translatable(session.scope == ClientSession.Scope.WORLD ? "controlmobspawns.gui.scope.world" : "controlmobspawns.gui.scope.global")));
		tabs.add(new GeneralTab());
		tabs.add(new MobsTab());
		tabs.add(new BiomeSpawnsTab());
		if (session.remote) {
			tabs.add(new CurrentMobsTab());
		}
	}

	public ClientSession session() {
		return session;
	}

	void open(Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	/** Rebuilds the header too, for tabs whose toolbar shows live data. */
	void reinit() {
		rebuildWidgets();
	}

	@Override
	protected List<Component> tabs() {
		return tabs.stream().map(Tab::title).toList();
	}

	@Override
	protected int selectedTab() {
		return tab;
	}

	public int tabCount() {
		return tabs.size();
	}

	/** Opens a tab directly, for example from tests. */
	public void showTab(int index) {
		selectTab(index);
	}

	@Override
	protected void onTabSelected(int index) {
		tabs.get(tab).onHide();
		tab = index;
		tabs.get(tab).onShow(this);
	}

	@Override
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected @Nullable LayoutElement toolbar(int rowWidth) {
		return tabs.get(tab).toolbar(this, rowWidth);
	}

	@Override
	protected List<RowList.Row> buildRows() {
		return tabs.get(tab).rows(this, search());
	}

	@Override
	protected boolean showDoneButton() {
		return false;
	}

	@Override
	protected void addFooterButtons(LinearLayout footer) {
		cancelButton = footer.addChild(Button.builder(Component.translatable("controlmobspawns.gui.close"), button -> onClose()).width(120).build());
		saveButton = footer.addChild(Button.builder(Component.translatable("controlmobspawns.gui.save"), button -> {
			session.save();
			updateFooter();
		}).width(120).build());
		updateFooter();
	}

	private void updateFooter() {
		boolean dirty = session.isDirty();
		if (saveButton != null) {
			saveButton.active = dirty;
		}
		if (cancelButton != null) {
			cancelButton.setMessage(Component.translatable(dirty ? "controlmobspawns.gui.cancel" : "controlmobspawns.gui.close"));
		}
	}

	@Override
	public void tick() {
		super.tick();
		updateFooter();
	}

	@Override
	public void added() {
		super.added();
		tabs.get(tab).onShow(this);
	}

	@Override
	public void removed() {
		super.removed();
		tabs.get(tab).onHide();
	}

	/** Leaving with unsaved changes asks first; the pending changes are then dropped. */
	@Override
	public void onClose() {
		if (!session.isDirty()) {
			minecraft.gui.setScreen(parent);
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(discard -> minecraft.gui.setScreen(discard ? parent : this),
			Component.translatable("controlmobspawns.gui.unsaved.title"),
			Component.translatable("controlmobspawns.gui.unsaved.message"),
			Component.translatable("controlmobspawns.gui.unsaved.discard"),
			Component.translatable("controlmobspawns.gui.unsaved.back")));
	}
}
