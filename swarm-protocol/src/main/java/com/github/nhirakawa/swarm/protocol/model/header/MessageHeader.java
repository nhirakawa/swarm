package com.github.nhirakawa.swarm.protocol.model.header;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * Fixed-length message header containing routing and metadata information.
 * Total size: 22 bytes
 * <p>
 * Layout:
 * - Metadata (4 bytes): version, type, compression, serialization
 * - Payload Length (2 bytes): size of message payload (max 65535 for UDP)
 * - Message ID (4 bytes): unique identifier for deduplication
 * - Timestamp (8 bytes): Unix timestamp in milliseconds
 * - Checksum (4 bytes): CRC32 over header + payload
 */
@Value.Builder
public record MessageHeader(
	MessageVersion messageVersion,
	SwarmMessageType type,
	Compression compression,
	Serialization serialization,
	int payloadLength,
	long messageId,
	long timestamp,
	long checksum
) {
	public static class Builder extends MessageHeaderBuilder {

		@Override
		public MessageHeader build() {
			MessageHeader messageHeader = super.build();
			Preconditions.checkState(
				messageHeader.payloadLength() >= 0,
				"payloadLength (%s) must be >= 0",
				messageHeader.payloadLength()
			);
			Preconditions.checkState(
				messageHeader.payloadLength() < 65535,
				"payloadLength (%s) must be < 65535",
				messageHeader.payloadLength()
			);
			return messageHeader;
		}
	}
}
