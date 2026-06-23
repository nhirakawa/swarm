package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.google.common.base.Preconditions;
import java.nio.ByteBuffer;

public class HeaderDeserializer {

  private static final int HEADER_SIZE = 22;

  public HeaderDeserializer() {}

  public MessageHeader deserialize(byte[] bytes) {
    Preconditions.checkArgument(
      bytes.length == HEADER_SIZE,
      "Expected %s bytes, got %s",
      HEADER_SIZE,
      bytes.length
    );

    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    // Metadata (4 bytes)
    MessageVersion messageVersion = MessageVersion.parse(buffer.get());
    SwarmMessageType messageType = SwarmMessageType.parse(buffer.get());
    Compression compression = Compression.parse(buffer.get());
    Serialization serialization = Serialization.parse(buffer.get());

    // Payload length (2 bytes)
    int payloadLength = buffer.getShort() & 0xFFFF; // Convert to unsigned

    // Message ID (4 bytes)
    long messageId = buffer.getInt() & 0xFFFFFFFFL; // Convert to unsigned

    // Timestamp (8 bytes)
    long timestamp = buffer.getLong();

    // Checksum (4 bytes)
    long checksum = buffer.getInt() & 0xFFFFFFFFL; // Convert to unsigned

    return new MessageHeader.Builder()
        .messageVersion(messageVersion)
        .type(messageType)
        .compression(compression)
        .serialization(serialization)
        .payloadLength(payloadLength)
        .messageId(messageId)
        .timestamp(timestamp)
        .checksum(checksum)
        .build();
  }
}
