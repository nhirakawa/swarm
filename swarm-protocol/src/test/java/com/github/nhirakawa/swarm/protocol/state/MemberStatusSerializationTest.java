package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.protocol.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import org.junit.jupiter.api.Test;

class MemberStatusSerializationTest {

	private static final ObjectMapper OBJECT_MAPPER =
		ObjectMapperWrapper.instance();
	private static final SwarmAddress ADDRESS = new InMemorySwarmAddress("asdf");

	@Test
	void testSerializeAndDeserializeAlive() throws Exception {
		MemberStatus.Alive original = new MemberStatus.Alive(ADDRESS, 5);

		String json = OBJECT_MAPPER.writeValueAsString(original);
		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isEqualTo(original);
		assertThat(deserialized).isInstanceOf(MemberStatus.Alive.class);
		assertThat(json).contains("\"type\":\"ALIVE\"");
	}

	@Test
	void testSerializeAndDeserializeSuspected() throws Exception {
		MemberStatus.Suspected original = new MemberStatus.Suspected(ADDRESS, 10);

		String json = OBJECT_MAPPER.writeValueAsString(original);
		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isEqualTo(original);
		assertThat(deserialized).isInstanceOf(MemberStatus.Suspected.class);
		assertThat(json).contains("\"type\":\"SUSPECTED\"");
	}

	@Test
	void testSerializeAndDeserializeConfirmed() throws Exception {
		MemberStatus.Confirmed original = new MemberStatus.Confirmed(ADDRESS, 3);

		String json = OBJECT_MAPPER.writeValueAsString(original);
		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isEqualTo(original);
		assertThat(deserialized).isInstanceOf(MemberStatus.Confirmed.class);
		assertThat(json).contains("\"type\":\"CONFIRMED\"");
	}

	@Test
	void testDeserializeAliveFromJson() throws Exception {
		String json = """
			{
			  "type": "ALIVE",
			  "address": "asdf",
			  "incarnation": 5
			}
			""";

		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isInstanceOf(MemberStatus.Alive.class);
		assertThat(deserialized.address()).isEqualTo(ADDRESS);
		assertThat(deserialized.incarnation()).isEqualTo(5);
	}

	@Test
	void testDeserializeSuspectedFromJson() throws Exception {
		String json = """
			{
			  "type": "SUSPECTED",
			  "address": "asdf",
			  "incarnation": 10
			}
			""";

		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isInstanceOf(MemberStatus.Suspected.class);
		assertThat(deserialized.address()).isEqualTo(ADDRESS);
		assertThat(deserialized.incarnation()).isEqualTo(10);
	}

	@Test
	void testDeserializeConfirmedFromJson() throws Exception {
		String json = """
			{
			  "type": "CONFIRMED",
			  "address": "asdf",
			  "incarnation": 3
			}
			""";

		MemberStatus deserialized = OBJECT_MAPPER.readValue(
			json,
			MemberStatus.class
		);

		assertThat(deserialized).isInstanceOf(MemberStatus.Confirmed.class);
		assertThat(deserialized.address()).isEqualTo(ADDRESS);
		assertThat(deserialized.incarnation()).isEqualTo(3);
	}
}
