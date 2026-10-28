package com.heet.controlmobspawns.client.screen;

import com.heet.controlmobspawns.client.ClientSession;
import com.heet.controlmobspawns.client.ControlMobSpawnsClient;
import com.heet.controlmobspawns.network.CmsPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** In a world: fetches the configuration from the server, then lets the player pick what to edit. */
public class HomeScreen extends Screen {
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private CmsPayloads.@Nullable Sync sync;
	private MultiLineTextWidget status;
	private Button globalButton;
	private Button worldButton;

	public HomeScreen(Screen parent) {
		super(Component.translatable("controlmobspawns.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addTitleHeader(title, font);
		LinearLayout content = layout.addToContents(LinearLayout.vertical().spacing(8));
		content.defaultCellSetting().alignHorizontallyCenter();
		status = content.addChild(new MultiLineTextWidget(Component.translatable("controlmobspawns.gui.loading"), font).setMaxWidth(300).setCentered(true));
		globalButton = content.addChild(Button.builder(Component.translatable("controlmobspawns.gui.home.global"), button -> open(ClientSession.Scope.GLOBAL))
			.width(220).tooltip(Tooltip.create(Component.translatable("controlmobspawns.gui.home.global.hint"))).build());
		worldButton = content.addChild(Button.builder(Component.translatable("controlmobspawns.gui.home.world"), button -> open(ClientSession.Scope.WORLD))
			.width(220).tooltip(Tooltip.create(Component.translatable("controlmobspawns.gui.home.world.hint"))).build());
		layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).width(200).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		updateState();

		ControlMobSpawnsClient.setSyncListener(received -> {
			sync = received;
			updateState();
		});
		if (ClientPlayNetworking.canSend(CmsPayloads.RequestSync.TYPE)) {
			ClientPlayNetworking.send(new CmsPayloads.RequestSync());
		} else {
			status.setMessage(Component.translatable("controlmobspawns.gui.home.missing_on_server"));
		}
	}

	private void updateState() {
		boolean ready = sync != null && sync.canEdit();
		globalButton.active = ready;
		worldButton.active = ready;
		if (sync != null) {
			status.setMessage(Component.translatable(sync.canEdit() ? "controlmobspawns.gui.home.ready" : "controlmobspawns.message.no_permission"));
		}
		repositionElements();
	}

	/** Null until the server answered, then whether this player may edit. */
	public @Nullable Boolean canEdit() {
		return sync == null ? null : sync.canEdit();
	}

	private void open(ClientSession.Scope scope) {
		if (sync != null) {
			ConfigScreens.open(parent, ClientSession.remote(scope, sync));
		}
	}

	@Override
	public void removed() {
		ControlMobSpawnsClient.setSyncListener(null);
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
