package pl.skidam.automodpack.mixin.core;

/*? if <26.2 {*/
/*
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

// JoinMultiplayerScreen only builds its footer through HeaderAndFooterLayout.visitWidgets starting
// in 26.2 - every earlier version (including the pre-1.19.4 case that never had LinearLayout /
// HeaderAndFooterLayout at all) composes it manually instead, so there is no matching injection
// target and this feature is a no-op there rather than a bespoke per-version reimplementation.
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}
}
*//*?} else {*/
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import pl.skidam.automodpack.client.ui.ModpackSelectionScreen;
import pl.skidam.automodpack.client.ui.versioned.VersionedText;

/**
 * Adds an "Optional Mods" button to the multiplayer screen's top footer row, beside Join Server /
 * Direct Connection / Add Server, reflecting whether the highlighted server is a known AutoModpack
 * modpack with optional groups. Requires 26.2: that's the version JoinMultiplayerScreen started
 * building its footer through HeaderAndFooterLayout.visitWidgets (the injection point below) and
 * LinearLayout gained a way to remove children again, letting the button be added to / removed from
 * the row on each selection change and the row re-centered so it appears and disappears cleanly.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

	@Shadow
	protected ServerSelectionList serverSelectionList;

	@Unique
	private LinearLayout automodpack$topRow;
	@Unique
	private final List<AbstractWidget> automodpack$vanillaRowButtons = new ArrayList<>();
	@Unique
	private boolean automodpack$buttonInRow = false;
	@Unique
	private Button automodpack$groupsButton;

	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}

	// Captured before the layout is walked into widgets. ordinal = 1 is the top footer row (0 is the
	// outer vertical footer, 2 the bottom row).
	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;visitWidgets(Ljava/util/function/Consumer;)V"))
	private void automodpack$captureRow(CallbackInfo ci, @Local(ordinal = 1) LinearLayout topFooterButtons) {
		automodpack$topRow = topFooterButtons;

		automodpack$groupsButton = Button.builder(VersionedText.translatable("automodpack.selection.button"), press -> {
			String address = automodpack$selectedServerAddress();
			if (address != null) minecraft.gui.setScreen(ModpackSelectionScreen.forServerAddress(this, address));
		}).width(100).build();

		automodpack$vanillaRowButtons.clear();
		topFooterButtons.visitChildren(child -> {
			if (child instanceof AbstractWidget widget) automodpack$vanillaRowButtons.add(widget);
		});
		automodpack$buttonInRow = false;
		automodpack$groupsButton.visible = false;
		addRenderableWidget(automodpack$groupsButton);
	}

	// Fires on every selection change (and once at the end of init). require = 0 so a version without
	// this exact method just leaves the button in its default state rather than failing to load.
	@Inject(method = "onSelectedChange", at = @At("RETURN"), require = 0)
	private void automodpack$onSelectedChange(CallbackInfo ci) {
		if (automodpack$topRow == null || automodpack$groupsButton == null) return;

		String address = automodpack$selectedServerAddress();
		boolean show = address != null && ModpackSelectionScreen.serverHasGroupsToConfigure(address);
		if (show == automodpack$buttonInRow) return; // Row membership already correct; avoid needless relayout.

		automodpack$topRow.removeChildren();
		for (AbstractWidget widget : automodpack$vanillaRowButtons) automodpack$topRow.addChild(widget);
		if (show) automodpack$topRow.addChild(automodpack$groupsButton);

		automodpack$groupsButton.visible = show;
		automodpack$buttonInRow = show;
		this.repositionElements(); // safe here: only re-arranges the layout and resizes the list
	}

	@Unique
	private String automodpack$selectedServerAddress() {
		if (serverSelectionList == null) return null;
		ObjectSelectionList.Entry<?> selected = serverSelectionList.getSelected();
		if (selected instanceof ServerSelectionList.OnlineServerEntry onlineEntry) {
			ServerData data = onlineEntry.getServerData();
			if (data != null) return data.ip;
		}
		return null;
	}
}
/*?}*/
