package com.github.nhirakawa.swarm.protocol.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.google.common.primitives.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeaderSerdeTest {

	private HeaderSerializer serializer;
	private HeaderDeserializer deserializer;

	private static final int PAYLOAD_LENGTH = 1234;
	private static final long MESSAGE_ID = 42L;
	private static final long TIMESTAMP = 0x176C323C000L;
	private static final long CHECKSUM = 0x12345678L;

	private static final byte[] PAYLOAD_LENGTH_BYTES = new byte[] {
		(byte) 0x04,
		(byte) 0xD2,
	};
	private static final byte[] MESSAGE_ID_BYTES = new byte[] {
		0,
		0,
		0,
		(byte) 0x2A,
	};
	private static final byte[] TIMESTAMP_BYTES = new byte[] {
		0,
		0,
		(byte) 0x01,
		(byte) 0x76,
		(byte) 0xC3,
		(byte) 0x23,
		(byte) 0xC0,
		0,
	};
	private static final byte[] CHECKSUM_BYTES = new byte[] {
		(byte) 0x12,
		(byte) 0x34,
		(byte) 0x56,
		(byte) 0x78,
	};

	@BeforeEach
	public void setup() {
		serializer = new HeaderSerializer();
		deserializer = new HeaderDeserializer();
	}

	@Test
	public void testSerializeBasicHeader() {
		MessageHeader header = new MessageHeader.Builder()
			.messageVersion(MessageVersion.V0)
			.type(SwarmMessageType.PING_REQUEST)
			.compression(Compression.NONE)
			.serialization(Serialization.JSON)
			.payloadLength(PAYLOAD_LENGTH)
			.messageId(MESSAGE_ID)
			.timestamp(TIMESTAMP)
			.checksum(CHECKSUM)
			.build();

		byte[] bytes = serializer.serialize(header);

		assertThat(bytes).hasSize(22);
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
		// Checksum (4 bytes: 0x12345678)
		assertThat(bytes[18]).isEqualTo((byte) 0x12);
		assertThat(bytes[19]).isEqualTo((byte) 0x34);
		assertThat(bytes[20]).isEqualTo((byte) 0x56);
		assertThat(bytes[21]).isEqualTo((byte) 0x78);
	}

	@Test
	public void testSerializeWithCompression() {
		MessageHeader header = new MessageHeader.Builder()
			.messageVersion(MessageVersion.V0)
			.type(SwarmMessageType.PING_ACK)
			.compression(Compression.GZIP)
			.serialization(Serialization.CBOR)
			.payloadLength(PAYLOAD_LENGTH)
			.messageId(MESSAGE_ID)
			.timestamp(TIMESTAMP)
			.checksum(CHECKSUM)
			.build();

		byte[] bytes = serializer.serialize(header);

		assertThat(bytes).hasSize(22);
		assertThat(bytes[0]).isEqualTo((byte) 0); // MessageVersion.V0
		assertThat(bytes[1]).isEqualTo((byte) 0); // PING_ACK
		assertThat(bytes[2]).isEqualTo((byte) 1); // GZIP
		assertThat(bytes[3]).isEqualTo((byte) 1); // CBOR
	}

	@Test
	public void testDeserializeBasicHeader() {
		byte[] bytes = Bytes.concat(
			new byte[] { 0, 1, 0, 0 }, // metadata
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		MessageHeader header = deserializer.deserialize(bytes);

		assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
		assertThat(header.type()).isEqualTo(SwarmMessageType.PING_REQUEST);
		assertThat(header.compression()).isEqualTo(Compression.NONE);
		assertThat(header.serialization()).isEqualTo(Serialization.JSON);
		assertThat(header.payloadLength()).isEqualTo(PAYLOAD_LENGTH);
		assertThat(header.messageId()).isEqualTo(MESSAGE_ID);
		assertThat(header.timestamp()).isEqualTo(TIMESTAMP);
		assertThat(header.checksum()).isEqualTo(CHECKSUM);
	}

	@Test
	public void testDeserializeWithCompression() {
		byte[] bytes = Bytes.concat(
			new byte[] { 0, 0, 1, 1 }, // metadata
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		MessageHeader header = deserializer.deserialize(bytes);

		assertThat(header.messageVersion()).isEqualTo(MessageVersion.V0);
		assertThat(header.type()).isEqualTo(SwarmMessageType.PING_ACK);
		assertThat(header.compression()).isEqualTo(Compression.GZIP);
		assertThat(header.serialization()).isEqualTo(Serialization.CBOR);
		assertThat(header.payloadLength()).isEqualTo(PAYLOAD_LENGTH);
		assertThat(header.messageId()).isEqualTo(MESSAGE_ID);
		assertThat(header.timestamp()).isEqualTo(TIMESTAMP);
		assertThat(header.checksum()).isEqualTo(CHECKSUM);
	}

	@Test
	public void testDeserializeThrowsOnInvalidLength() {
		byte[] tooShort = new byte[] { 0, 1, 0 };
		byte[] tooLong = Bytes.concat(
			new byte[] { 0, 1, 0, 0 },
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES,
			new byte[] { 0 } // extra byte → 35 total
		);

		assertThatThrownBy(() -> deserializer.deserialize(tooShort))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Expected 22 bytes, got 3");

		assertThatThrownBy(() -> deserializer.deserialize(tooLong))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Expected 22 bytes, got 23");
	}

	@Test
	public void testDeserializeThrowsOnInvalidMessageVersion() {
		byte[] invalidVersion = Bytes.concat(
			new byte[] { 99, 0, 0, 0 }, // invalid version
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		assertThatThrownBy(() -> deserializer.deserialize(invalidVersion))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("is not a valid MessageVersion");
	}

	@Test
	public void testDeserializeThrowsOnInvalidMessageType() {
		byte[] invalidType = Bytes.concat(
			new byte[] { 0, 99, 0, 0 }, // invalid type
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		assertThatThrownBy(() -> deserializer.deserialize(invalidType))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("is not a valid SwarmMessageType");
	}

	@Test
	public void testDeserializeThrowsOnInvalidCompression() {
		byte[] invalidCompression = Bytes.concat(
			new byte[] { 0, 0, 99, 0 }, // invalid compression
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		assertThatThrownBy(() -> deserializer.deserialize(invalidCompression))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("is not a valid Compression");
	}

	@Test
	public void testDeserializeThrowsOnInvalidSerialization() {
		byte[] invalidSerialization = Bytes.concat(
			new byte[] { 0, 0, 0, 99 }, // invalid serialization
			PAYLOAD_LENGTH_BYTES,
			MESSAGE_ID_BYTES,
			TIMESTAMP_BYTES,
			CHECKSUM_BYTES
		);

		assertThatThrownBy(() -> deserializer.deserialize(invalidSerialization))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("is not a valid Serialization");
	}

	@Test
	public void testRoundtripPingRequestWithJsonAndNoCompression() {
		MessageHeader original = new MessageHeader.Builder()
			.messageVersion(MessageVersion.V0)
			.type(SwarmMessageType.PING_REQUEST)
			.compression(Compression.NONE)
			.serialization(Serialization.JSON)
			.payloadLength(PAYLOAD_LENGTH)
			.messageId(MESSAGE_ID)
			.timestamp(TIMESTAMP)
			.checksum(CHECKSUM)
			.build();

		byte[] serialized = serializer.serialize(original);
		MessageHeader deserialized = deserializer.deserialize(serialized);

		assertThat(deserialized).isEqualTo(original);
	}

	@Test
	public void testRoundtripPingAckWithCborAndGzip() {
		MessageHeader original = new MessageHeader.Builder()
			.messageVersion(MessageVersion.V0)
			.type(SwarmMessageType.PING_ACK)
			.compression(Compression.GZIP)
			.serialization(Serialization.CBOR)
			.payloadLength(PAYLOAD_LENGTH)
			.messageId(MESSAGE_ID)
			.timestamp(TIMESTAMP)
			.checksum(CHECKSUM)
			.build();

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
						MessageHeader original = new MessageHeader.Builder()
							.messageVersion(version)
							.type(type)
							.compression(compression)
							.serialization(serialization)
							.payloadLength(PAYLOAD_LENGTH)
							.messageId(MESSAGE_ID)
							.timestamp(TIMESTAMP)
							.checksum(CHECKSUM)
							.build();

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
