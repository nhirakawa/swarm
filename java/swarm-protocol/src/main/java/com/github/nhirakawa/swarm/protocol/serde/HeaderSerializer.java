package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.google.common.primitives.UnsignedBytes;
import java.nio.ByteBuffer;

public class HeaderSerializer {

	private static final int HEADER_SIZE = 22;

	public HeaderSerializer() {}

	public byte[] serialize(MessageHeader header) {
		ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);

		// Metadata (4 bytes)
		buffer.put(UnsignedBytes.checkedCast(header.messageVersion().value()));
		buffer.put(UnsignedBytes.checkedCast(header.type().value()));
		buffer.put(UnsignedBytes.checkedCast(header.compression().value()));
		buffer.put(UnsignedBytes.checkedCast(header.serialization().value()));

		// Payload length (2 bytes)
		buffer.putShort((short) header.payloadLength());

		// Message ID (4 bytes)
		buffer.putInt((int) header.messageId());

		// Timestamp (8 bytes)
		buffer.putLong(header.timestamp());

		// Checksum (4 bytes)
		buffer.putInt((int) header.checksum());

		return buffer.array();
	}
}
