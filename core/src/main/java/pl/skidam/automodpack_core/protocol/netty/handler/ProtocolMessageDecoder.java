package pl.skidam.automodpack_core.protocol.netty.handler;

import static pl.skidam.automodpack_core.protocol.NetUtils.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import pl.skidam.automodpack_core.protocol.NetUtils;
import pl.skidam.automodpack_core.protocol.netty.message.request.EchoMessage;
import pl.skidam.automodpack_core.protocol.netty.message.request.FileRequestMessage;
import pl.skidam.automodpack_core.protocol.netty.message.request.FileResponseMessage;
import pl.skidam.automodpack_core.protocol.netty.message.request.PeerAnnounceMessage;
import pl.skidam.automodpack_core.protocol.netty.message.request.PeerListRequestMessage;
import pl.skidam.automodpack_core.protocol.netty.message.request.RefreshRequestMessage;

public class ProtocolMessageDecoder extends ByteToMessageDecoder {
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		in.markReaderIndex();
		byte version = in.readByte();
		byte type = in.readByte();
		byte[] secret = new byte[32];
		in.readBytes(secret);

		switch (type) {
			case ECHO_TYPE :
				int dataLength = in.readInt();
				byte[] data = new byte[dataLength];
				in.readBytes(data);
				out.add(new EchoMessage(version, secret, data));
				break;
			case FILE_REQUEST_TYPE :
				int fileHashLength = in.readInt();
				byte[] fileHash = new byte[fileHashLength];
				in.readBytes(fileHash);
				out.add(new FileRequestMessage(version, secret, fileHash));
				break;
			case NetUtils.FILE_RESPONSE_TYPE :
				int fileLength = in.readInt();
				byte[] fileData = new byte[fileLength];
				in.readBytes(fileData);
				out.add(new FileResponseMessage(version, secret, fileData));
				break;
			case REFRESH_REQUEST_TYPE :
				int fileHashesCount = in.readInt();
				int fileHashesLength = in.readInt();
				byte[][] fileHashesList = new byte[fileHashesCount][];
				for (int i = 0; i < fileHashesCount; i++) {
					byte[] fileHashEntry = new byte[fileHashesLength];
					in.readBytes(fileHashEntry);
					fileHashesList[i] = fileHashEntry;
				}
				out.add(new RefreshRequestMessage(version, secret, fileHashesList));
				break;
			case PEER_ANNOUNCE_TYPE :
				int lanHostLength = in.readInt();
				byte[] lanHostBytes = new byte[lanHostLength];
				in.readBytes(lanHostBytes);
				int lanPort = in.readInt();
				int tokenLength = in.readInt();
				byte[] token = new byte[tokenLength];
				in.readBytes(token);
				out.add(new PeerAnnounceMessage(version, secret, new String(lanHostBytes, StandardCharsets.UTF_8), lanPort, token));
				break;
			case PEER_LIST_REQUEST_TYPE :
				out.add(new PeerListRequestMessage(version, secret));
				break;
			default :
				throw new IllegalArgumentException("Unknown message type: " + type);
		}
	}
}
