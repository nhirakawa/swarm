package com.github.nhirakawa.swarm.protocol.model.header;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import java.util.Arrays;

/**
 * Fixed-length message header containing routing and metadata information.
 * Total size: 34 bytes
 *
 * Layout:
 * - Metadata (4 bytes): version, type, compression, serialization
 * - Payload Length (2 bytes): size of message payload (max 65535 for UDP)
 * - Message ID (4 bytes): unique identifier for deduplication
 * - Timestamp (8 bytes): Unix timestamp in milliseconds
 * - Source Address (6 bytes): IPv4 address (4) + port (2)
 * - Target Address (6 bytes): IPv4 address (4) + port (2)
 * - Checksum (4 bytes): CRC32 over header + payload
 */
public record MessageHeader(
  MessageVersion messageVersion,
  SwarmMessageType type,
  Compression compression,
  Serialization serialization,
  int payloadLength,
  long messageId,
  long timestamp,
  byte[] sourceIp,
  int sourcePort,
  byte[] targetIp,
  int targetPort,
  long checksum
) {
  public MessageHeader {
    if (payloadLength < 0 || payloadLength > 65535) {
      throw new IllegalArgumentException(
        "payloadLength must be 0-65535 for 2-byte length field, got " +
        payloadLength
      );
    }
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
      payloadLength == other.payloadLength &&
      messageId == other.messageId &&
      timestamp == other.timestamp &&
      Arrays.equals(sourceIp, other.sourceIp) &&
      sourcePort == other.sourcePort &&
      Arrays.equals(targetIp, other.targetIp) &&
      targetPort == other.targetPort &&
      checksum == other.checksum
    );
  }

  @Override
  public int hashCode() {
    int result = messageVersion.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + compression.hashCode();
    result = 31 * result + serialization.hashCode();
    result = 31 * result + payloadLength;
    result = 31 * result + Long.hashCode(messageId);
    result = 31 * result + Long.hashCode(timestamp);
    result = 31 * result + Arrays.hashCode(sourceIp);
    result = 31 * result + sourcePort;
    result = 31 * result + Arrays.hashCode(targetIp);
    result = 31 * result + targetPort;
    result = 31 * result + Long.hashCode(checksum);
    return result;
  }
}
