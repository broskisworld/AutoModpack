package pl.skidam.automodpack_core.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.config.Jsons;

class PeerRegistryTest {
	private Jsons.ServerConfigFieldsV3 previousConfig;
	private final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 12345);

	@BeforeEach
	void setUp() {
		previousConfig = Constants.serverConfig;
		Constants.serverConfig = new Jsons.ServerConfigFieldsV3();
		Constants.serverConfig.lanPeerSharingEnabled = true;
		// isSecretValid() short-circuits to true when this is off, letting this test exercise
		// PeerRegistry's own logic without standing up the file-backed SecretsStore/GAME_CALL.
		Constants.serverConfig.validateSecrets = false;
	}

	@AfterEach
	void tearDown() {
		Constants.serverConfig = previousConfig;
	}

	@Test
	void listsAnnouncedPeersExcludingTheRequester() {
		PeerRegistry registry = new PeerRegistry();
		registry.announce("uuid-a", "Alice", "192.168.1.10", 4000, new byte[]{1}, "secret-a", address);
		registry.announce("uuid-b", "Bob", "192.168.1.11", 4001, new byte[]{2}, "secret-b", address);

		List<PeerRegistry.PeerAnnouncement> peers = registry.listPeers("uuid-a");

		assertEquals(1, peers.size());
		assertEquals("uuid-b", peers.get(0).uuid());
		assertEquals("Bob", peers.get(0).playerName());
	}

	@Test
	void returnsNothingWhenSharingIsDisabled() {
		Constants.serverConfig.lanPeerSharingEnabled = false;
		PeerRegistry registry = new PeerRegistry();
		registry.announce("uuid-a", "Alice", "192.168.1.10", 4000, new byte[]{1}, "secret-a", address);

		assertTrue(registry.listPeers(null).isEmpty());
	}

	@Test
	void reAnnouncingTheSamePlayerReplacesTheirEntry() {
		PeerRegistry registry = new PeerRegistry();
		registry.announce("uuid-a", "Alice", "192.168.1.10", 4000, new byte[]{1}, "secret-a", address);
		registry.announce("uuid-a", "Alice", "192.168.1.99", 5000, new byte[]{9}, "secret-a", address);

		List<PeerRegistry.PeerAnnouncement> peers = registry.listPeers(null);

		assertEquals(1, peers.size());
		assertEquals("192.168.1.99", peers.get(0).lanHost());
		assertEquals(5000, peers.get(0).lanPort());
	}

	@Test
	void removeDropsThePlayerImmediately() {
		PeerRegistry registry = new PeerRegistry();
		registry.announce("uuid-a", "Alice", "192.168.1.10", 4000, new byte[]{1}, "secret-a", address);
		registry.remove("uuid-a");

		assertTrue(registry.listPeers(null).isEmpty());
	}
}
