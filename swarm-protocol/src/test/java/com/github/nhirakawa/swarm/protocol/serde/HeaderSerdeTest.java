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
      Serialization.JSON
    );

    byte[] bytes = serializer.serialize(header);

    assertThat(bytes).hasSize(4);
    assertThat(bytes[0]).isEqualTo((byte) 0); // MessageVersion.V0
    assertThat(bytes[1]).isEqualTo((byte) 1); // PING_REQUEST
    assertThat(bytes[2]).isEqualTo((byte) 0); // NONE
    assertThat(bytes[3]).isEqualTo((byte) 0); // JSON
  }

  @Test
  public void testSerializeWithCompression() {
    MessageHeader header = new MessageHeader(
      MessageVersion.V0,
      SwarmMessageType.PING_ACK,
      Compression.GZIP,
      Serialization.CBOR
    );

    byte[] bytes = serializer.serialize(header);

    assertThat(bytes).hasSize(4);
    assertThat(bytes[0]).isEqualTo((byte) 0); // MessageVersion.V0
    assertThat(bytes[1]).isEqualTo((byte) 0); // PING_ACK
    assertThat(bytes[2]).isEqualTo((byte) 1); // GZIP
    assertThat(bytes[3]).isEqualTo((byte) 1); // CBOR
  }

  @Test
  public void testDeserializeBasicHeader() {
    byte[] bytes = new byte[] { 0, 1, 0, 0 };

    MessageHeader header = deserializer.deserialize(bytes);

    assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
    assertThat(header.type()).isEqualTo(SwarmMessageType.PING_REQUEST);
    assertThat(header.compression()).isEqualTo(Compression.NONE);
    assertThat(header.serialization()).isEqualTo(Serialization.JSON);
  }

  @Test
  public void testDeserializeWithCompression() {
    byte[] bytes = new byte[] { 0, 0, 1, 1 };

    MessageHeader header = deserializer.deserialize(bytes);

    assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
    assertThat(header.type()).isEqualTo(SwarmMessageType.PING_ACK);
    assertThat(header.compression()).isEqualTo(Compression.GZIP);
    assertThat(header.serialization()).isEqualTo(Serialization.CBOR);
  }

  @Test
  public void testDeserializeThrowsOnInvalidLength() {
    byte[] tooShort = new byte[] { 0, 1, 0 };
    byte[] tooLong = new byte[] { 0, 1, 0, 0, 1 };

    assertThatThrownBy(() -> deserializer.deserialize(tooShort))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 4 bytes, got 3");

    assertThatThrownBy(() -> deserializer.deserialize(tooLong))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Expected 4 bytes, got 5");
  }

  @Test
  public void testDeserializeThrowsOnInvalidMessageVersion() {
    byte[] invalidVersion = new byte[] { 99, 0, 0, 0 };

    assertThatThrownBy(() -> deserializer.deserialize(invalidVersion))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid MessageVersion");
  }

  @Test
  public void testDeserializeThrowsOnInvalidMessageType() {
    byte[] invalidType = new byte[] { 0, 99, 0, 0 };

    assertThatThrownBy(() -> deserializer.deserialize(invalidType))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid SwarmMessageType");
  }

  @Test
  public void testDeserializeThrowsOnInvalidCompression() {
    byte[] invalidCompression = new byte[] { 0, 0, 99, 0 };

    assertThatThrownBy(() -> deserializer.deserialize(invalidCompression))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("is not a valid Compression");
  }

  @Test
  public void testDeserializeThrowsOnInvalidSerialization() {
    byte[] invalidSerialization = new byte[] { 0, 0, 0, 99 };

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
      Serialization.JSON
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
      Serialization.CBOR
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
              serialization
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
