package com.github.nhirakawa.swarm.protocol.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SwarmMessageType {
	PING_ACK(0),
	PING_REQUEST(1),
	DISCOVERY_REQUEST(2),
	DISCOVERY_RESPONSE(3);

	private final int value;

	SwarmMessageType(int value) {
		this.value = value;
	}

	@JsonCreator
	public static SwarmMessageType parse(int value) {
		return switch (value) {
			case 0 -> SwarmMessageType.PING_ACK;
			case 1 -> SwarmMessageType.PING_REQUEST;
			case 2 -> SwarmMessageType.DISCOVERY_REQUEST;
			case 3 -> SwarmMessageType.DISCOVERY_RESPONSE;
			default -> throw new IllegalArgumentException(
				"%d is not a valid SwarmMessageType value".formatted(value)
			);
		};
	}

	@JsonValue
	public int value() {
		return value;
	}
}
