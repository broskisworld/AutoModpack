package pl.skidam.automodpack_core.protocol.netty.message.request;

import static pl.skidam.automodpack_core.protocol.NetUtils.PEER_ANNOUNCE_TYPE;

import pl.skidam.automodpack_core.protocol.netty.message.ProtocolMessage;

public class PeerAnnounceMessage extends ProtocolMessage {
	private final String lanHost;
	private final int lanPort;
	private final byte[] token;

	public PeerAnnounceMessage(byte version, byte[] secret, String lanHost, int lanPort, byte[] token) {
		super(version, PEER_ANNOUNCE_TYPE, secret);
		this.lanHost = lanHost;
		this.lanPort = lanPort;
		this.token = token;
	}

	public String getLanHost() {
		return lanHost;
	}

	public int getLanPort() {
		return lanPort;
	}

	public byte[] getToken() {
		return token;
	}
}
