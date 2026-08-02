package pl.skidam.automodpack_core.protocol.peer;

import static org.junit.jupiter.api.Assertions.*;
import static pl.skidam.automodpack_core.Constants.storeDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.skidam.automodpack_core.utils.HashUtils;

class PeerHostServerTest {

	private final HttpClient http = HttpClient.newHttpClient();
	private PeerHostServer server;
	private String sha1;
	private byte[] fileContent;

	@BeforeEach
	void setUp() throws IOException {
		Files.createDirectories(storeDir);
		fileContent = "hello from a LAN peer".getBytes();
		sha1 = HashUtils.getHash(writeTempStoreFile(fileContent));
		server = new PeerHostServer();
		server.setAllowedHashes(Set.of(sha1));
	}

	@AfterEach
	void tearDown() throws IOException {
		if (server != null) server.stop();
		if (Files.isDirectory(storeDir)) {
			try (Stream<Path> stream = Files.walk(storeDir)) {
				stream.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException ignored) {
					}
				});
			}
		}
	}

	@Test
	void servesAnAllowedHashWithAValidToken() throws Exception {
		int port = server.start();

		HttpResponse<byte[]> response = http.send(request(port, sha1, server.getToken()), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(200, response.statusCode());
		assertArrayEquals(fileContent, response.body());
	}

	@Test
	void rejectsAnInvalidToken() throws Exception {
		int port = server.start();
		byte[] wrongToken = new byte[32];

		HttpResponse<byte[]> response = http.send(request(port, sha1, wrongToken), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(403, response.statusCode());
	}

	@Test
	void rejectsAHashOutsideTheAllowList() throws Exception {
		int port = server.start();
		String notAllowed = "a".repeat(40);

		HttpResponse<byte[]> response = http.send(request(port, notAllowed, server.getToken()), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(404, response.statusCode());
	}

	@Test
	void rejectsAMalformedHash() throws Exception {
		int port = server.start();

		HttpResponse<byte[]> response = http.send(request(port, "not-a-sha1", server.getToken()), HttpResponse.BodyHandlers.ofByteArray());

		assertEquals(404, response.statusCode());
	}

	@Test
	void rejectsPathTraversalAttemptsInThePathSegment() throws Exception {
		int port = server.start();
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(server.getToken());
		HttpRequest traversal = HttpRequest.newBuilder()
				.uri(URI.create("http://" + host() + ":" + port + "/automodpack/peer/..%2F..%2Fautomodpack-content.json?token=" + token)).GET().build();

		HttpResponse<byte[]> response = http.send(traversal, HttpResponse.BodyHandlers.ofByteArray());

		// Regardless of how the HTTP layer parses the traversal attempt, the file must never be served.
		assertNotEquals(200, response.statusCode());
	}

	private HttpRequest request(int port, String hash, byte[] token) {
		String encodedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
		return HttpRequest.newBuilder().uri(URI.create("http://" + host() + ":" + port + "/automodpack/peer/" + hash + "?token=" + encodedToken)).GET()
				.build();
	}

	// The server binds to whatever AddressHelpers.getLocalIp() reports (falling back to the
	// wildcard address, which also accepts loopback) - tests must talk to that same address
	// rather than assuming 127.0.0.1, since a bind restricted to a specific LAN interface would
	// otherwise refuse loopback connections.
	private String host() {
		String boundHost = server.getBoundHost();
		return boundHost != null ? boundHost : "127.0.0.1";
	}

	private Path writeTempStoreFile(byte[] content) throws IOException {
		Path temp = Files.createTempFile(storeDir, "staging", ".tmp");
		Files.write(temp, content);
		String hash = HashUtils.getHash(temp);
		Path destination = storeDir.resolve(hash);
		Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}
}
