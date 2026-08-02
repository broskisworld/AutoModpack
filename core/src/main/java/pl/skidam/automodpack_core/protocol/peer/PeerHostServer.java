package pl.skidam.automodpack_core.protocol.peer;

import static pl.skidam.automodpack_core.Constants.LOGGER;
import static pl.skidam.automodpack_core.Constants.storeDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import pl.skidam.automodpack_core.utils.AddressHelpers;
import pl.skidam.automodpack_core.utils.SmartFileUtils;

/**
 * The "serving" half of LAN peer sharing: a tiny embedded HTTP server that hands out files
 * this client already has locally verified, to other players on the same private network.
 * It never trusts the network for anything - every request is checked against three
 * independent gates before a single byte is streamed:
 *
 * 1. The remote address must be on a private/local subnet ({@link AddressHelpers#isLocal}).
 * 2. The caller must present the current session's capability token, which is only ever
 * handed out to players the origin Minecraft server has already authenticated and relayed
 * through the existing authenticated protocol connection (see PeerRegistry/PEER_LIST_RESPONSE).
 * 3. The requested hash must be part of the modpack file set this client was told to serve
 * (an explicit allow-list, never arbitrary local-store contents), and the file's bytes are
 * re-verified against that hash immediately before streaming.
 */
public class PeerHostServer {

	private static final int MAX_CONCURRENT_REQUESTS = 4;
	private static final String PATH_PREFIX = "/automodpack/peer/";
	private static final Pattern SHA1_HEX = Pattern.compile("^[0-9a-fA-F]{40}$");

	private final byte[] token = new byte[32];
	private final Set<String> allowedHashes = ConcurrentHashMap.newKeySet();
	private HttpServer server;
	private ExecutorService executor;
	private String boundHost;

	public PeerHostServer() {
		new SecureRandom().nextBytes(token);
	}

	public byte[] getToken() {
		return token.clone();
	}

	public void setAllowedHashes(Set<String> hashes) {
		allowedHashes.clear();
		allowedHashes.addAll(hashes);
	}

	public synchronized int start() throws IOException {
		if (server != null) return server.getAddress().getPort();

		boundHost = AddressHelpers.getLocalIp();
		InetSocketAddress bindAddress = boundHost != null ? new InetSocketAddress(boundHost, 0) : new InetSocketAddress(0);

		server = HttpServer.create(bindAddress, 0);
		executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS, r -> {
			Thread thread = new Thread(r, "AutoModpack Peer Host");
			thread.setDaemon(true);
			return thread;
		});
		server.setExecutor(executor);
		server.createContext(PATH_PREFIX, this::handle);
		server.start();
		// A null boundHost here means no non-loopback interface was found; the server still listens
		// (on every interface, including loopback) but there is no address worth handing to a peer.
		return server.getAddress().getPort();
	}

	/** The LAN address this server actually bound to - the address other peers must be told to use. */
	public synchronized String getBoundHost() {
		return boundHost;
	}

	public synchronized void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
		boundHost = null;
	}

	public synchronized boolean isRunning() {
		return server != null;
	}

	private void handle(HttpExchange exchange) {
		try {
			if (!isRequestAuthorized(exchange)) {
				sendStatus(exchange, 403);
				return;
			}

			String sha1 = extractHash(exchange);
			if (sha1 == null || !SHA1_HEX.matcher(sha1).matches() || !allowedHashes.contains(sha1.toLowerCase(Locale.ROOT))) {
				sendStatus(exchange, 404);
				return;
			}

			Path file = storeDir.resolve(sha1.toLowerCase(Locale.ROOT));
			if (!Files.isRegularFile(file)) {
				sendStatus(exchange, 404);
				return;
			}

			long size = Files.size(file);
			if (!SmartFileUtils.isValidFile(file, size, sha1)) {
				sendStatus(exchange, 404);
				return;
			}

			exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
			exchange.sendResponseHeaders(200, size);
			try (OutputStream out = exchange.getResponseBody()) {
				Files.copy(file, out);
			}
		} catch (Exception e) {
			LOGGER.warn("Error serving LAN peer request", e);
			try {
				sendStatus(exchange, 500);
			} catch (IOException ignored) {
			}
		} finally {
			exchange.close();
		}
	}

	private boolean isRequestAuthorized(HttpExchange exchange) {
		InetSocketAddress remote = exchange.getRemoteAddress();
		if (remote == null || remote.getAddress() == null) return false;
		if (!AddressHelpers.isLocal(remote.getAddress().getHostAddress())) return false;

		String providedTokenParam = queryParam(exchange.getRequestURI().getQuery(), "token");
		if (providedTokenParam == null) return false;

		byte[] provided;
		try {
			provided = Base64.getUrlDecoder().decode(providedTokenParam);
		} catch (IllegalArgumentException e) {
			return false;
		}

		return MessageDigest.isEqual(provided, token);
	}

	private String extractHash(HttpExchange exchange) {
		String path = exchange.getRequestURI().getPath();
		if (!path.startsWith(PATH_PREFIX)) return null;
		return path.substring(PATH_PREFIX.length());
	}

	private static String queryParam(String query, String key) {
		if (query == null) return null;
		for (String part : query.split("&")) {
			int eq = part.indexOf('=');
			if (eq < 0) continue;
			if (part.substring(0, eq).equals(key)) return part.substring(eq + 1);
		}
		return null;
	}

	private static void sendStatus(HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
	}
}
