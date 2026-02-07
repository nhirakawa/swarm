package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.google.common.primitives.UnsignedBytes;
import java.nio.ByteBuffer;

public class HeaderSerializer {

  private static final int HEADER_SIZE = 18;

  public HeaderSerializer() {}

  public byte[] serialize(MessageHeader header) {
    ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);

    // Metadata (4 bytes)
    buffer.put(UnsignedBytes.checkedCast(header.messageVersion().value()));
    buffer.put(UnsignedBytes.checkedCast(header.type().value()));
    buffer.put(UnsignedBytes.checkedCast(header.compression().value()));
    buffer.put(UnsignedBytes.checkedCast(header.serialization().value()));

    // Source address (4 bytes IP + 2 bytes port = 6 bytes)
    buffer.put(header.sourceIp());
    buffer.putShort((short) header.sourcePort());

    // Target address (4 bytes IP + 2 bytes port = 6 bytes)
    buffer.put(header.targetIp());
    buffer.putShort((short) header.targetPort());

    // Payload length (2 bytes)
    buffer.putShort((short) header.payloadLength());

    return buffer.array();
  }
}
