package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.serde.HeaderDeserializer;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StdioReceiver
	extends AbstractExecutionThreadService
	implements SwarmMessageReceiver
{

	private static final Logger LOG = LogManager.getLogger(StdioReceiver.class);
	private static final int HEADER_SIZE = 22;

	private final BufferedReader bufferedReader;
	private final BlockingQueue<StateMachineMessage> queue;
	private final ObjectReader objectReader;
	private final HeaderDeserializer headerDeserializer;

	StdioReceiver(ObjectReader objectReader) {
		this(System.in, objectReader);
	}

	StdioReceiver(InputStream inputStream, ObjectReader objectReader) {
		this.bufferedReader = new BufferedReader(
			new InputStreamReader(inputStream)
		);
		this.queue = new ArrayBlockingQueue<>(100);
		this.objectReader = objectReader;
		this.headerDeserializer = new HeaderDeserializer();
	}

	@Override
	public Optional<StateMachineMessage> receive(Duration timeout)
		throws InterruptedException {
		return Optional.ofNullable(
			queue.poll(timeout.toNanos(), TimeUnit.NANOSECONDS)
		);
	}

	@Override
	protected void run() throws Exception {
		String line;
		try {
			while ((line = bufferedReader.readLine()) != null) {
				try {
					processLine(line);
				} catch (Exception e) {
					LOG.error("Failed to process message", e);
				}
			}
		} catch (IOException e) {
			if (isRunning()) {
				throw e;
			}
		}
	}

	private void processLine(String line) throws Exception {
		JsonNode envelope = objectReader.readTree(line);
		byte[] payloadBytes = Base64.getDecoder().decode(
			envelope.get("payload").asText()
		);

		byte[] headerBytes = Arrays.copyOf(payloadBytes, HEADER_SIZE);
		MessageHeader header = headerDeserializer.deserialize(headerBytes);

		byte[] bodyBytes = Arrays.copyOfRange(
			payloadBytes,
			HEADER_SIZE,
			payloadBytes.length
		);
		StateMachineMessage message = deserializeBody(bodyBytes, header.type());

		queue.put(message);
	}

	private StateMachineMessage deserializeBody(
		byte[] bodyBytes,
		SwarmMessageType type
	) throws IOException {
		return switch (type) {
			case PING_REQUEST -> objectReader.readValue(bodyBytes, PingRequest.class);
			case PING_ACK -> objectReader.readValue(bodyBytes, PingAck.class);
			case DISCOVERY_REQUEST -> objectReader.readValue(
				bodyBytes,
				DiscoveryRequest.class
			);
			case DISCOVERY_RESPONSE -> objectReader.readValue(
				bodyBytes,
				DiscoveryResponse.class
			);
		};
	}

	@Override
	protected void triggerShutdown() {
		try {
			bufferedReader.close();
		} catch (IOException e) {
			LOG.error("Could not close BufferedReader", e);
		}
	}

	@Override
	protected void shutDown() throws Exception {
		bufferedReader.close();
	}
}
