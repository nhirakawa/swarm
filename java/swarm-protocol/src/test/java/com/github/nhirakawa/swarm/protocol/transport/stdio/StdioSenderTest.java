package com.github.nhirakawa.swarm.protocol.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StdioSenderTest {

	private static final ObjectMapper OBJECT_MAPPER = buildObjectMapper();
	private static final StdioAddress LOCAL = new StdioAddress("node-1");

	@Test
	void testWritesPacketLine() throws Exception {
		DiscoveryRequest message = new DiscoveryRequest(
			LOCAL,
			StdioAddress.BROADCAST
		);

		JsonNode line = sendAndReadLines(sender ->
			sender.send(message, Duration.ofSeconds(1))
		).get(0);

		assertThat(line.get("source").asText()).isEqualTo("node-1");
		assertThat(line.get("target").asText()).isEqualTo("__all__");
		assertThat(line.has("control")).isFalse();

		byte[] frame = Base64.getDecoder().decode(line.get("packet").asText());
		String body = new String(frame, 22, frame.length - 22, StandardCharsets.UTF_8);
		assertThat(OBJECT_MAPPER.readTree(body)).isEqualTo(
			OBJECT_MAPPER.readTree(
				"{\"source\":\"node-1\",\"target\":\"__all__\"}"
			)
		);
	}

	@Test
	void testWritesControlLineToRouter() throws Exception {
		JsonNode line = sendAndReadLines(sender ->
			sender.sendControl(Map.of("kind", "ready"), Duration.ofSeconds(1))
		).get(0);

		assertThat(line).isEqualTo(
			OBJECT_MAPPER.readTree(
				"{\"source\":\"node-1\",\"target\":\"__router__\",\"control\":{\"kind\":\"ready\"}}"
			)
		);
	}

	@FunctionalInterface
	private interface SenderAction {
		boolean run(StdioSender sender) throws Exception;
	}

	private static List<JsonNode> sendAndReadLines(SenderAction action)
		throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		StdioSender sender = new StdioSender(LOCAL, output, OBJECT_MAPPER.writer());
		sender.startAsync().awaitRunning(5, TimeUnit.SECONDS);

		assertThat(action.run(sender)).isTrue();

		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (output.size() == 0 && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		sender.stopAsync().awaitTerminated(5, TimeUnit.SECONDS);

		return output
			.toString(StandardCharsets.UTF_8)
			.lines()
			.map(line -> {
				try {
					return OBJECT_MAPPER.readTree(line);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			})
			.toList();
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
