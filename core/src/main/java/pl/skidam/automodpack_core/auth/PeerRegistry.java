package pl.skidam.automodpack_core.auth;

import static pl.skidam.automodpack_core.Constants.serverConfig;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live-session LAN peer presence, kept in memory only (never persisted - this is
 * "who's currently online and opted in", not identity). Every listing re-validates
 * each entry through {@link Secrets#isSecretValid} - the same check the file-transfer
 * protocol relies on - so a disconnected, banned, or un-whitelisted player's stale
 * announcement is never handed out to other players.
 */
public class PeerRegistry {

	public record PeerAnnouncement(String uuid, String playerName, String lanHost, int lanPort, byte[] token, String secret, SocketAddress remoteAddress) {}

	private final Map<String, PeerAnnouncement> peers = new ConcurrentHashMap<>();

	public void announce(String uuid, String playerName, String lanHost, int lanPort, byte[] token, String secret, SocketAddress remoteAddress) {
		peers.put(uuid, new PeerAnnouncement(uuid, playerName, lanHost, lanPort, token, secret, remoteAddress));
	}

	public void remove(String uuid) {
		peers.remove(uuid);
	}

	public List<PeerAnnouncement> listPeers(String excludingUuid) {
		if (!serverConfig.lanPeerSharingEnabled) return List.of();

		List<PeerAnnouncement> result = new ArrayList<>();
		for (Map.Entry<String, PeerAnnouncement> entry : peers.entrySet()) {
			if (entry.getKey().equals(excludingUuid)) continue;

			PeerAnnouncement announcement = entry.getValue();
			if (!Secrets.isSecretValid(announcement.secret(), announcement.remoteAddress())) {
				peers.remove(entry.getKey(), announcement);
				continue;
			}

			result.add(announcement);
		}
		return result;
	}
}
