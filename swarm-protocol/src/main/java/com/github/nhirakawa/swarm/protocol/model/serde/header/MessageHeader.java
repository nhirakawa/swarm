package com.github.nhirakawa.swarm.protocol.model.serde.header;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import java.util.Arrays;

/**
 * Fixed-length message header containing routing and metadata information.
 * IP addresses are stored as 4-byte arrays for IPv4.
 * Payload length is stored as a 2-byte value (max 65535 bytes for UDP).
 */
public record MessageHeader(
  MessageVersion messageVersion,
  SwarmMessageType type,
  Compression compression,
  Serialization serialization,
  byte[] sourceIp,
  int sourcePort,
  byte[] targetIp,
  int targetPort,
  int payloadLength
) {
  public MessageHeader {
    if (sourceIp.length != 4) {
      throw new IllegalArgumentException(
        "sourceIp must be 4 bytes for IPv4, got " + sourceIp.length
      );
    }
    if (targetIp.length != 4) {
      throw new IllegalArgumentException(
        "targetIp must be 4 bytes for IPv4, got " + targetIp.length
      );
    }
    if (payloadLength < 0 || payloadLength > 65535) {
      throw new IllegalArgumentException(
        "payloadLength must be 0-65535 for 2-byte length field, got " +
        payloadLength
      );
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MessageHeader other)) {
      return false;
    }

    return (
      messageVersion == other.messageVersion &&
      type == other.type &&
      compression == other.compression &&
      serialization == other.serialization &&
      Arrays.equals(sourceIp, other.sourceIp) &&
      sourcePort == other.sourcePort &&
      Arrays.equals(targetIp, other.targetIp) &&
      targetPort == other.targetPort &&
      payloadLength == other.payloadLength
    );
  }

  @Override
  public int hashCode() {
    int result = messageVersion.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + compression.hashCode();
    result = 31 * result + serialization.hashCode();
    result = 31 * result + Arrays.hashCode(sourceIp);
    result = 31 * result + sourcePort;
    result = 31 * result + Arrays.hashCode(targetIp);
    result = 31 * result + targetPort;
    result = 31 * result + payloadLength;
    return result;
  }
}
