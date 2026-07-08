package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.nhirakawa.swarm.protocol.ObjectMapperWrapper;
import org.junit.jupiter.api.Test;

class InMemorySwarmAddressDeserializerTest {

	private static final ObjectMapper OBJECT_MAPPER =
		ObjectMapperWrapper.instance();

	@Test
	void testDeserializesString() throws Exception {
		InMemorySwarmAddress address = OBJECT_MAPPER.readValue(
			"\"node-1\"",
			InMemorySwarmAddress.class
		);
		assertThat(address).isEqualTo(new InMemorySwarmAddress("node-1"));
	}

	@Test
	void testRejectsNumber() {
		assertThatThrownBy(() ->
			OBJECT_MAPPER.readValue("123", InMemorySwarmAddress.class)
		).isInstanceOf(InvalidFormatException.class);
	}

	@Test
	void testRejectsBoolean() {
		assertThatThrownBy(() ->
			OBJECT_MAPPER.readValue("true", InMemorySwarmAddress.class)
		).isInstanceOf(InvalidFormatException.class);
	}

	@Test
	void testRejectsObject() {
		assertThatThrownBy(() ->
			OBJECT_MAPPER.readValue(
				"{\"address\":\"node-1\"}",
				InMemorySwarmAddress.class
			)
		).isInstanceOf(InvalidFormatException.class);
	}

	@Test
	void testRejectsArray() {
		assertThatThrownBy(() ->
			OBJECT_MAPPER.readValue("[\"node-1\"]", InMemorySwarmAddress.class)
		).isInstanceOf(InvalidFormatException.class);
	}
}
