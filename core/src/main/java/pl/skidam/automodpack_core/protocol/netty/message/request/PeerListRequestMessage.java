package pl.skidam.automodpack_core.protocol.netty.message.request;

import static pl.skidam.automodpack_core.protocol.NetUtils.PEER_LIST_REQUEST_TYPE;

import pl.skidam.automodpack_core.protocol.netty.message.ProtocolMessage;

public class PeerListRequestMessage extends ProtocolMessage {
	public PeerListRequestMessage(byte version, byte[] secret) {
		super(version, PEER_LIST_REQUEST_TYPE, secret);
	}
}
