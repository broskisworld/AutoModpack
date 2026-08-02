package pl.skidam.automodpack.client.ui;

import java.util.List;

import net.minecraft.ChatFormatting;

import pl.skidam.automodpack.client.audio.AudioManager;
import pl.skidam.automodpack.client.ui.versioned.VersionedMatrices;
import pl.skidam.automodpack.client.ui.versioned.VersionedScreen;
import pl.skidam.automodpack.client.ui.versioned.VersionedText;
import pl.skidam.automodpack_core.protocol.DownloadClient;
import pl.skidam.automodpack_loader_core.client.ModpackUpdater;

public class LanPeerScreen extends VersionedScreen {

	private static final int MAX_LISTED_PEERS = 8;

	private final ModpackUpdater modpackUpdater;
	private final List<DownloadClient.PeerInfo> peers;
	private boolean decisionFinished;

	public LanPeerScreen(ModpackUpdater modpackUpdater, List<DownloadClient.PeerInfo> peers) {
		super(VersionedText.literal("LanPeerScreen"));
		this.modpackUpdater = modpackUpdater;
		this.peers = peers;

		if (AudioManager.isMusicPlaying()) AudioManager.stopMusic();
	}

	@Override
	protected void init() {
		super.init();

		this.addRenderableWidget(buttonWidget(this.width / 2 - 115, this.height / 2 + 80, 120, 20, VersionedText.translatable("automodpack.lanpeers.deny"),
				button -> respond(false)));

		this.addRenderableWidget(buttonWidget(this.width / 2 + 15, this.height / 2 + 80, 120, 20,
				VersionedText.translatable("automodpack.lanpeers.allow").withStyle(ChatFormatting.BOLD), button -> respond(true)));
	}

	private void respond(boolean allow) {
		if (decisionFinished) return;
		decisionFinished = true;
		modpackUpdater.resumeAfterPeerConsent(allow);
	}

	@Override
	public void versionedRender(VersionedMatrices matrices, int mouseX, int mouseY, float delta) {
		int lineHeight = 12;
		int top = this.height / 2 - 80;

		drawCenteredTextWithShadow(matrices, this.font, VersionedText.translatable("automodpack.lanpeers.title").withStyle(ChatFormatting.BOLD), this.width / 2,
				top, TextColors.WHITE);

		drawCenteredTextWithShadow(matrices, this.font, VersionedText.translatable("automodpack.lanpeers.description"), this.width / 2, top + lineHeight * 2,
				TextColors.WHITE);

		int listed = Math.min(peers.size(), MAX_LISTED_PEERS);
		for (int i = 0; i < listed; i++) {
			DownloadClient.PeerInfo peer = peers.get(i);
			String line = peer.playerName() + "  (" + peer.lanHost() + ":" + peer.lanPort() + ")";
			drawCenteredTextWithShadow(matrices, this.font, VersionedText.literal(line), this.width / 2, top + lineHeight * (5 + i), TextColors.GRAY);
		}
		if (peers.size() > listed) {
			drawCenteredTextWithShadow(matrices, this.font, VersionedText.literal("+" + (peers.size() - listed) + " more"), this.width / 2,
					top + lineHeight * (5 + listed), TextColors.GRAY);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
