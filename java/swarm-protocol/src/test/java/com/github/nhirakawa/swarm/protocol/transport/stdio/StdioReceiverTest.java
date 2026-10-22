package com.github.nhirakawa.swarm.protocol.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.serde.HeaderSerializer;
import com.google.common.primitives.Bytes;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StdioReceiverTest {

	private static final ObjectMapper OBJECT_MAPPER = buildObjectMapper();
	private static final HeaderSerializer HEADER_SERIALIZER =
		new HeaderSerializer();

	private static final StdioAddress SOURCE = new StdioAddress("node-1");
	private static final StdioAddress TARGET = new StdioAddress("node-2");
	private static final PingRequest MESSAGE = new PingRequest(
		SOURCE,
		TARGET,
		Optional.empty(),
		4L,
		List.of()
	);

	@Test
	void testReceivesValidFrame() throws Exception {
		byte[] body = OBJECT_MAPPER.writeValueAsBytes(MESSAGE);
		byte[] frame = Bytes.concat(header(body.length), body);

		StdioReceiver receiver = runReceiver(envelope(frame));

		assertThat(receiver.receive(Duration.ofMillis(10))).contains(MESSAGE);
	}

	@Test
	void testDropsFrameShorterThanHeader() throws Exception {
		byte[] frame = Arrays.copyOf(header(0), 10);

		StdioReceiver receiver = runReceiver(envelope(frame));

		assertThat(receiver.receive(Duration.ofMillis(10))).isEmpty();
	}

	@Test
	void testDropsFrameWithMismatchedPayloadLength() throws Exception {
		byte[] body = OBJECT_MAPPER.writeValueAsBytes(MESSAGE);
		byte[] frame = Bytes.concat(header(body.length + 1), body);

		StdioReceiver receiver = runReceiver(envelope(frame));

		assertThat(receiver.receive(Duration.ofMillis(10))).isEmpty();
	}

	@Test
	void testContinuesAfterInvalidFrame() throws Exception {
		byte[] body = OBJECT_MAPPER.writeValueAsBytes(MESSAGE);
		byte[] invalidFrame = Arrays.copyOf(header(0), 10);
		byte[] validFrame = Bytes.concat(header(body.length), body);

		StdioReceiver receiver = runReceiver(
			envelope(invalidFrame) + "\n" + envelope(validFrame)
		);

		Optional<StateMachineMessage> result = receiver.receive(
			Duration.ofMillis(10)
		);
		assertThat(result).contains(MESSAGE);
		assertThat(receiver.receive(Duration.ofMillis(10))).isEmpty();
	}

	private static StdioReceiver runReceiver(String input) throws Exception {
		StdioReceiver receiver = new StdioReceiver(
			new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
			OBJECT_MAPPER.reader()
		);
		// run() returns once the input stream is exhausted
		receiver.startAsync().awaitTerminated(5, TimeUnit.SECONDS);
		return receiver;
	}

	private static byte[] header(int payloadLength) {
		return HEADER_SERIALIZER.serialize(
			new MessageHeader.Builder()
				.messageVersion(MessageVersion.V0)
				.type(MESSAGE.type())
				.compression(Compression.NONE)
				.serialization(Serialization.JSON)
				.payloadLength(payloadLength)
				.messageId(1)
				.timestamp(0)
				.checksum(0)
				.build()
		);
	}

	private static String envelope(byte[] frame) throws Exception {
		return OBJECT_MAPPER.writeValueAsString(
			Map.of(
				"source",
				SOURCE.asString(),
				"target",
				TARGET.asString(),
				"payload",
				Base64.getEncoder().encodeToString(frame)
			)
		);
	}

	private static ObjectMapper buildObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.registerModule(new Jdk8Module());
		objectMapper.registerModule(new GuavaModule());

		SimpleModule module = new SimpleModule();
		module.addAbstractTypeMapping(SwarmAddress.class, StdioAddress.class);
		module.addSerializer(new StdioSwarmAddressSerializer());
		module.addDeserializer(
			StdioAddress.class,
			new StdioSwarmAddressDeserializer()
		);
		objectMapper.registerModule(module);

		return objectMapper;
	}
}
