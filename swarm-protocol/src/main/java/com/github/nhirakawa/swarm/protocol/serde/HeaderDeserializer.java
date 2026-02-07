package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Serialization;
import com.google.common.base.Preconditions;
import java.nio.ByteBuffer;

public class HeaderDeserializer {

  private static final int HEADER_SIZE = 18;

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

    // Source address (4 bytes IP + 2 bytes port = 6 bytes)
    byte[] sourceIp = new byte[4];
    buffer.get(sourceIp);
    int sourcePort = buffer.getShort() & 0xFFFF; // Convert to unsigned

    // Target address (4 bytes IP + 2 bytes port = 6 bytes)
    byte[] targetIp = new byte[4];
    buffer.get(targetIp);
    int targetPort = buffer.getShort() & 0xFFFF; // Convert to unsigned

    // Payload length (2 bytes)
    int payloadLength = buffer.getShort() & 0xFFFF; // Convert to unsigned

    return new MessageHeader(
      messageVersion,
      messageType,
      compression,
      serialization,
      sourceIp,
      sourcePort,
      targetIp,
      targetPort,
      payloadLength
    );
  }
}
