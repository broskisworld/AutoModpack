package pl.skidam.automodpack_core.protocol.netty.handler;

import static org.junit.jupiter.api.Assertions.*;
import static pl.skidam.automodpack_core.protocol.NetUtils.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import pl.skidam.automodpack_core.Constants;
import pl.skidam.automodpack_core.auth.Secrets;
import pl.skidam.automodpack_core.auth.SecretsStore;
import pl.skidam.automodpack_core.config.Jsons;
import pl.skidam.automodpack_core.protocol.netty.NettyServer;

class ServerMessageHandlerTest {
	private Jsons.ServerConfigFieldsV3 previousConfig;

	@BeforeEach
	void setUp() {
		previousConfig = Constants.serverConfig;
		Constants.serverConfig = new Jsons.ServerConfigFieldsV3();
	}

	@AfterEach
	void tearDown() throws Exception {
		Constants.serverConfig = previousConfig;
		if (Files.isDirectory(Constants.privateDir)) {
			try (Stream<Path> stream = Files.walk(Constants.privateDir)) {
				stream.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception ignored) {
					}
				});
			}
		}
	}

	@Test
	void peerAnnounceIsRejectedWhenLanSharingIsDisabled() {
		Constants.serverConfig.lanPeerSharingEnabled = false;
		Constants.serverConfig.validateSecrets = false;
		EmbeddedChannel channel = newChannel();

		channel.writeInbound(peerAnnounceFrame(new byte[32], "192.168.1.10", 4000, new byte[]{1}));

		assertErrorAndClosed(channel);
	}

	@Test
	void peerListRequestIsRejectedWhenLanSharingIsDisabled() {
		Constants.serverConfig.lanPeerSharingEnabled = false;
		Constants.serverConfig.validateSecrets = false;
		EmbeddedChannel channel = newChannel();

		channel.writeInbound(peerListRequestFrame(new byte[32]));

		assertErrorAndClosed(channel);
	}

	@Test
	void announcedPeerIsListedToOtherAuthenticatedPlayersButNotToItself() throws Exception {
		Constants.serverConfig.lanPeerSharingEnabled = true;
		NettyServer server = new NettyServer();

		String uuidA = UUID.randomUUID().toString();
		String uuidB = UUID.randomUUID().toString();
		byte[] secretBytesA = saveHostSecret(uuidA);
		byte[] secretBytesB = saveHostSecret(uuidB);
		byte[] token = {9, 8, 7};

		EmbeddedChannel channelA = newChannelFor(server);
		channelA.writeInbound(peerAnnounceFrame(secretBytesA, "192.168.1.10", 4000, token));
		assertAckAndOpen(channelA);

		EmbeddedChannel channelB = newChannelFor(server);
		channelB.writeInbound(peerListRequestFrame(secretBytesB));
		ByteBuf response = channelB.readOutbound();
		try {
			assertEquals(LATEST_SUPPORTED_PROTOCOL_VERSION, response.readByte());
			assertEquals(PEER_LIST_RESPONSE_TYPE, response.readByte());
			assertEquals(1, response.readInt());
			assertEquals(uuidA, readUtf(response));
			assertEquals(uuidA, readUtf(response)); // NullGameCall.getPlayerName echoes the uuid back as the name
			assertEquals("192.168.1.10", readUtf(response));
			assertEquals(4000, response.readInt());
			byte[] receivedToken = new byte[response.readInt()];
			response.readBytes(receivedToken);
			assertArrayEquals(token, receivedToken);
		} finally {
			response.release();
			channelA.finishAndReleaseAll();
			channelB.finishAndReleaseAll();
		}
	}

	@Test
	void aPlayerNeverSeesThemselvesInTheirOwnPeerList() throws Exception {
		Constants.serverConfig.lanPeerSharingEnabled = true;
		NettyServer server = new NettyServer();

		String uuidA = UUID.randomUUID().toString();
		byte[] secretBytesA = saveHostSecret(uuidA);

		EmbeddedChannel channelA = newChannelFor(server);
		channelA.writeInbound(peerAnnounceFrame(secretBytesA, "192.168.1.10", 4000, new byte[]{1}));
		assertAckAndOpen(channelA);

		channelA.writeInbound(peerListRequestFrame(secretBytesA));
		ByteBuf response = channelA.readOutbound();
		try {
			assertEquals(LATEST_SUPPORTED_PROTOCOL_VERSION, response.readByte());
			assertEquals(PEER_LIST_RESPONSE_TYPE, response.readByte());
			assertEquals(0, response.readInt());
		} finally {
			response.release();
			channelA.finishAndReleaseAll();
		}
	}

	private byte[] saveHostSecret(String uuid) {
		Secrets.Secret secret = Secrets.generateSecret();
		SecretsStore.saveHostSecret(uuid, secret);
		return secret.secretBytes();
	}

	private EmbeddedChannel newChannel() {
		return newChannelFor(new NettyServer());
	}

	private EmbeddedChannel newChannelFor(NettyServer server) {
		EmbeddedChannel channel = new EmbeddedChannel(new ProtocolMessageDecoder(), new ServerMessageHandler(server));
		channel.attr(NettyServer.PROTOCOL_VERSION).set(LATEST_SUPPORTED_PROTOCOL_VERSION);
		channel.attr(NettyServer.CHUNK_SIZE).set(DEFAULT_CHUNK_SIZE);
		channel.attr(NettyServer.REAL_REMOTE_ADDR).set(new InetSocketAddress("127.0.0.1", 12345));
		return channel;
	}

	private static ByteBuf peerAnnounceFrame(byte[] secret, String host, int port, byte[] token) {
		byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
		ByteBuf buf = Unpooled.buffer();
		buf.writeByte(LATEST_SUPPORTED_PROTOCOL_VERSION);
		buf.writeByte(PEER_ANNOUNCE_TYPE);
		buf.writeBytes(secret);
		buf.writeInt(hostBytes.length);
		buf.writeBytes(hostBytes);
		buf.writeInt(port);
		buf.writeInt(token.length);
		buf.writeBytes(token);
		return buf;
	}

	private static ByteBuf peerListRequestFrame(byte[] secret) {
		ByteBuf buf = Unpooled.buffer();
		buf.writeByte(LATEST_SUPPORTED_PROTOCOL_VERSION);
		buf.writeByte(PEER_LIST_REQUEST_TYPE);
		buf.writeBytes(secret);
		return buf;
	}

	private static String readUtf(ByteBuf buf) {
		byte[] bytes = new byte[buf.readInt()];
		buf.readBytes(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void assertAckAndOpen(EmbeddedChannel channel) {
		ByteBuf response = channel.readOutbound();
		try {
			assertEquals(LATEST_SUPPORTED_PROTOCOL_VERSION, response.readByte());
			assertEquals(END_OF_TRANSMISSION, response.readByte());
			assertTrue(channel.isActive());
		} finally {
			response.release();
		}
	}

	private static void assertErrorAndClosed(EmbeddedChannel channel) {
		ByteBuf response = channel.readOutbound();
		try {
			assertEquals(LATEST_SUPPORTED_PROTOCOL_VERSION, response.readByte());
			assertEquals(ERROR, response.readByte());
		} finally {
			response.release();
			channel.finishAndReleaseAll();
		}
	}
}
