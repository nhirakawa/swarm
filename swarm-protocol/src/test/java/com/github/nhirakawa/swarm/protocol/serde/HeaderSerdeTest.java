package com.github.nhirakawa.swarm.protocol.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Serialization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeaderSerdeTest {

  private HeaderSerializer serializer;
  private HeaderDeserializer deserializer;

  private static final int PAYLOAD_LENGTH = 1234;
  private static final long MESSAGE_ID = 42L;
  private static final long TIMESTAMP = 1609591668736L; // 0x176C323C000
  private static final byte[] SOURCE_IP = new byte[] { (byte) 192, (byte) 168, 1, 1 };
  private static final int SOURCE_PORT = 8080;
  private static final byte[] TARGET_IP = new byte[] { (byte) 192, (byte) 168, 1, 2 };
  private static final int TARGET_PORT = 9090;
  private static final long CHECKSUM = 0x12345678L;

  @BeforeEach
  public void setup() {
    serializer = new HeaderSerializer();
    deserializer = new HeaderDeserializer();
  }

  @Test
  public void testSerializeBasicHeader() {
    MessageHeader header = new MessageHeader(
      MessageVersion.V0,
      SwarmMessageType.PING_REQUEST,
      Compression.NONE,
      Serialization.JSON,
      PAYLOAD_LENGTH,
      MESSAGE_ID,
      TIMESTAMP,
      SOURCE_IP,
      SOURCE_PORT,
      TARGET_IP,
      TARGET_PORT,
      CHECKSUM
    );

    byte[] bytes = serializer.serialize(header);

    assertThat(bytes).hasSize(34);
    // Metadata (4 bytes)
    assertThat(bytes[0]).isEqualTo((byte) 0); // MessageVersion.V0
    assertThat(bytes[1]).isEqualTo((byte) 1); // PING_REQUEST
    assertThat(bytes[2]).isEqualTo((byte) 0); // NONE
    assertThat(bytes[3]).isEqualTo((byte) 0); // JSON
    // Payload length (2 bytes: 1234 = 0x04D2)
    assertThat(bytes[4]).isEqualTo((byte) 0x04);
    assertThat(bytes[5]).isEqualTo((byte) 0xD2);
    // Message ID (4 bytes: 42 = 0x0000002A)
    assertThat(bytes[6]).isEqualTo((byte) 0x00);
    assertThat(bytes[7]).isEqualTo((byte) 0x00);
    assertThat(bytes[8]).isEqualTo((byte) 0x00);
    assertThat(bytes[9]).isEqualTo((byte) 0x2A);
    // Timestamp (8 bytes: 1609591668736L = 0x000176C323C000)
    assertThat(bytes[10]).isEqualTo((byte) 0x00);
    assertThat(bytes[11]).isEqualTo((byte) 0x00);
    assertThat(bytes[12]).isEqualTo((byte) 0x01);
    assertThat(bytes[13]).isEqualTo((byte) 0x76);
    assertThat(bytes[14]).isEqualTo((byte) 0xC3);
    assertThat(bytes[15]).isEqualTo((byte) 0x23);
    assertThat(bytes[16]).isEqualTo((byte) 0xC0);
    assertThat(bytes[17]).isEqualTo((byte) 0x00);
    // Source IP (4 bytes)
    assertThat(bytes[18]).isEqualTo((byte) 192);
    assertThat(bytes[19]).isEqualTo((byte) 168);
    assertThat(bytes[20]).isEqualTo((byte) 1);
    assertThat(bytes[21]).isEqualTo((byte) 1);
    // Source port (2 bytes: 8080 = 0x1F90)
    assertThat(bytes[22]).isEqualTo((byte) 0x1F);
    assertThat(bytes[23]).isEqualTo((byte) 0x90);
    // Target IP (4 bytes)
    assertThat(bytes[24]).isEqualTo((byte) 192);
    assertThat(bytes[25]).isEqualTo((byte) 168);
    assertThat(bytes[26]).isEqualTo((byte) 1);
    assertThat(bytes[27]).isEqualTo((byte) 2);
    // Target port (2 bytes: 9090 = 0x2382)
    assertThat(bytes[28]).isEqualTo((byte) 0x23);
    assertThat(bytes[29]).isEqualTo((byte) 0x82);
    // Checksum (4 bytes: 0x12345678)
    assertThat(bytes[30]).isEqualTo((byte) 0x12);
    assertThat(bytes[31]).isEqualTo((byte) 0x34);
    assertThat(bytes[32]).isEqualTo((byte) 0x56);
    assertThat(bytes[33]).isEqualTo((byte) 0x78);
  }

  @Test
  public void testSerializeWithCompression() {
    MessageHeader header = new MessageHeader(
      MessageVersion.V0,
      SwarmMessageType.PING_ACK,
      Compression.GZIP,
      Serialization.CBOR,
      PAYLOAD_LENGTH,
      MESSAGE_ID,
      TIMESTAMP,
      SOURCE_IP,
      SOURCE_PORT,
      TARGET_IP,
      TARGET_PORT,
      CHECKSUM
    );

    byte[] bytes = serializer.serialize(header);

    assertThat(bytes).hasSize(34);
    assertThat(bytes[0]).isEqualTo((byte) 0); // MessageVersion.V0
    assertThat(bytes[1]).isEqualTo((byte) 0); // PING_ACK
    assertThat(bytes[2]).isEqualTo((byte) 1); // GZIP
    assertThat(bytes[3]).isEqualTo((byte) 1); // CBOR
  }

  @Test
  public void testDeserializeBasicHeader() {
    byte[] bytes = new byte[] {
      0, 1, 0, 0,                                // metadata
      (byte) 0x04, (byte) 0xD2,                  // payload length (1234)
      0, 0, 0, (byte) 0x2A,                      // message ID (42)
      0, 0, (byte) 0x01, (byte) 0x76,            // timestamp (1609591668736L)
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,              // source IP
      (byte) 0x1F, (byte) 0x90,                  // source port (8080)
      (byte) 192, (byte) 168, 1, 2,              // target IP
      (byte) 0x23, (byte) 0x82,                  // target port (9090)
      (byte) 0x12, (byte) 0x34,                  // checksum (0x12345678)
      (byte) 0x56, (byte) 0x78
    };

    MessageHeader header = deserializer.deserialize(bytes);

    assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
    assertThat(header.type()).isEqualTo(SwarmMessageType.PING_REQUEST);
    assertThat(header.compression()).isEqualTo(Compression.NONE);
    assertThat(header.serialization()).isEqualTo(Serialization.JSON);
    assertThat(header.payloadLength()).isEqualTo(PAYLOAD_LENGTH);
    assertThat(header.messageId()).isEqualTo(MESSAGE_ID);
    assertThat(header.timestamp()).isEqualTo(TIMESTAMP);
    assertThat(header.sourceIp()).isEqualTo(SOURCE_IP);
    assertThat(header.sourcePort()).isEqualTo(SOURCE_PORT);
    assertThat(header.targetIp()).isEqualTo(TARGET_IP);
    assertThat(header.targetPort()).isEqualTo(TARGET_PORT);
    assertThat(header.checksum()).isEqualTo(CHECKSUM);
  }

  @Test
  public void testDeserializeWithCompression() {
    byte[] bytes = new byte[] {
      0, 0, 1, 1,                                // metadata
      (byte) 0x04, (byte) 0xD2,                  // payload length (1234)
      0, 0, 0, (byte) 0x2A,                      // message ID (42)
      0, 0, (byte) 0x01, (byte) 0x76,            // timestamp (1609591668736L)
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,              // source IP
      (byte) 0x1F, (byte) 0x90,                  // source port (8080)
      (byte) 192, (byte) 168, 1, 2,              // target IP
      (byte) 0x23, (byte) 0x82,                  // target port (9090)
      (byte) 0x12, (byte) 0x34,                  // checksum (0x12345678)
      (byte) 0x56, (byte) 0x78
    };

    MessageHeader header = deserializer.deserialize(bytes);

    assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
    assertThat(header.type()).isEqualTo(SwarmMessageType.PING_ACK);
    assertThat(header.compression()).isEqualTo(Compression.GZIP);
    assertThat(header.serialization()).isEqualTo(Serialization.CBOR);
    assertThat(header.payloadLength()).isEqualTo(PAYLOAD_LENGTH);
    assertThat(header.messageId()).isEqualTo(MESSAGE_ID);
    assertThat(header.timestamp()).isEqualTo(TIMESTAMP);
    assertThat(header.sourceIp()).isEqualTo(SOURCE_IP);
    assertThat(header.sourcePort()).isEqualTo(SOURCE_PORT);
    assertThat(header.targetIp()).isEqualTo(TARGET_IP);
    assertThat(header.targetPort()).isEqualTo(TARGET_PORT);
    assertThat(header.checksum()).isEqualTo(CHECKSUM);
  }

  @Test
  public void testDeserializeThrowsOnInvalidLength() {
    byte[] tooShort = new byte[] { 0, 1, 0 };
    byte[] tooLong = new byte[] {
      0, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
      16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
    };

    assertThatThrownBy(() -> deserializer.deserialize(tooShort))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 34 bytes, got 3");

    assertThatThrownBy(() -> deserializer.deserialize(tooLong))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 34 bytes, got 35");
  }

  @Test
  public void testDeserializeThrowsOnInvalidMessageVersion() {
    byte[] invalidVersion = new byte[] {
      99, 0, 0, 0,                               // invalid version
      (byte) 0x04, (byte) 0xD2,
      0, 0, 0, (byte) 0x2A,
      0, 0, (byte) 0x01, (byte) 0x76,
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,
      (byte) 0x1F, (byte) 0x90,
      (byte) 192, (byte) 168, 1, 2,
      (byte) 0x23, (byte) 0x82,
      (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    assertThatThrownBy(() -> deserializer.deserialize(invalidVersion))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid MessageVersion");
  }

  @Test
  public void testDeserializeThrowsOnInvalidMessageType() {
    byte[] invalidType = new byte[] {
      0, 99, 0, 0,                               // invalid type
      (byte) 0x04, (byte) 0xD2,
      0, 0, 0, (byte) 0x2A,
      0, 0, (byte) 0x01, (byte) 0x76,
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,
      (byte) 0x1F, (byte) 0x90,
      (byte) 192, (byte) 168, 1, 2,
      (byte) 0x23, (byte) 0x82,
      (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    assertThatThrownBy(() -> deserializer.deserialize(invalidType))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid SwarmMessageType");
  }

  @Test
  public void testDeserializeThrowsOnInvalidCompression() {
    byte[] invalidCompression = new byte[] {
      0, 0, 99, 0,                               // invalid compression
      (byte) 0x04, (byte) 0xD2,
      0, 0, 0, (byte) 0x2A,
      0, 0, (byte) 0x01, (byte) 0x76,
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,
      (byte) 0x1F, (byte) 0x90,
      (byte) 192, (byte) 168, 1, 2,
      (byte) 0x23, (byte) 0x82,
      (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    assertThatThrownBy(() -> deserializer.deserialize(invalidCompression))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid Compression");
  }

  @Test
  public void testDeserializeThrowsOnInvalidSerialization() {
    byte[] invalidSerialization = new byte[] {
      0, 0, 0, 99,                               // invalid serialization
      (byte) 0x04, (byte) 0xD2,
      0, 0, 0, (byte) 0x2A,
      0, 0, (byte) 0x01, (byte) 0x76,
      (byte) 0xC3, (byte) 0x23, (byte) 0xC0, 0,
      (byte) 192, (byte) 168, 1, 1,
      (byte) 0x1F, (byte) 0x90,
      (byte) 192, (byte) 168, 1, 2,
      (byte) 0x23, (byte) 0x82,
      (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78
    };

    assertThatThrownBy(() -> deserializer.deserialize(invalidSerialization))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid Serialization");
  }

  @Test
  public void testRoundtripPingRequestWithJsonAndNoCompression() {
    MessageHeader original = new MessageHeader(
      MessageVersion.V0,
      SwarmMessageType.PING_REQUEST,
      Compression.NONE,
      Serialization.JSON,
      PAYLOAD_LENGTH,
      MESSAGE_ID,
      TIMESTAMP,
      SOURCE_IP,
      SOURCE_PORT,
      TARGET_IP,
      TARGET_PORT,
      CHECKSUM
    );

    byte[] serialized = serializer.serialize(original);
    MessageHeader deserialized = deserializer.deserialize(serialized);

    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void testRoundtripPingAckWithCborAndGzip() {
    MessageHeader original = new MessageHeader(
      MessageVersion.V0,
      SwarmMessageType.PING_ACK,
      Compression.GZIP,
      Serialization.CBOR,
      PAYLOAD_LENGTH,
      MESSAGE_ID,
      TIMESTAMP,
      SOURCE_IP,
      SOURCE_PORT,
      TARGET_IP,
      TARGET_PORT,
      CHECKSUM
    );

    byte[] serialized = serializer.serialize(original);
    MessageHeader deserialized = deserializer.deserialize(serialized);

    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  public void testRoundtripAllCombinations() {
    for (MessageVersion version : MessageVersion.values()) {
      for (SwarmMessageType type : SwarmMessageType.values()) {
        for (Compression compression : Compression.values()) {
          for (Serialization serialization : Serialization.values()) {
            MessageHeader original = new MessageHeader(
              version,
              type,
              compression,
              serialization,
              PAYLOAD_LENGTH,
              MESSAGE_ID,
              TIMESTAMP,
              SOURCE_IP,
              SOURCE_PORT,
              TARGET_IP,
              TARGET_PORT,
              CHECKSUM
            );

            byte[] serialized = serializer.serialize(original);
            MessageHeader deserialized = deserializer.deserialize(serialized);

            assertThat(deserialized)
              .withFailMessage(
                "Roundtrip failed for: version=%s, type=%s, compression=%s, serialization=%s",
                version,
                type,
                compression,
                serialization
              )
              .isEqualTo(original);
          }
        }
      }
    }
  }
}
