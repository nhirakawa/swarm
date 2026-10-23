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
import com.google.common.base.Preconditions;
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
import java.util.function.Consumer;
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
	private final Consumer<JsonNode> controlHandler;
	private final Runnable endOfInputListener;

	private volatile boolean stopping = false;

	StdioReceiver(
		ObjectReader objectReader,
		Consumer<JsonNode> controlHandler,
		Runnable endOfInputListener
	) {
		this(System.in, objectReader, controlHandler, endOfInputListener);
	}

	/**
	 * @param controlHandler receives the {@code control} object of each control line
	 * @param endOfInputListener runs once when stdin reaches end of input, which
	 *     means the router is gone
	 */
	StdioReceiver(
		InputStream inputStream,
		ObjectReader objectReader,
		Consumer<JsonNode> controlHandler,
		Runnable endOfInputListener
	) {
		this.bufferedReader = new BufferedReader(
			new InputStreamReader(inputStream)
		);
		this.queue = new ArrayBlockingQueue<>(100);
		this.objectReader = objectReader;
		this.headerDeserializer = new HeaderDeserializer();
		this.controlHandler = controlHandler;
		this.endOfInputListener = endOfInputListener;
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
			if (isRunning() && !stopping) {
				throw e;
			}
		}

		if (!stopping) {
			LOG.info("Reached end of input on stdin");
			endOfInputListener.run();
		}
	}

	private void processLine(String line) throws Exception {
		JsonNode envelope = objectReader.readTree(line);
		boolean hasPacket = envelope.has("packet");
		boolean hasControl = envelope.has("control");
		Preconditions.checkArgument(
			hasPacket != hasControl,
			"Expected exactly one of packet or control, got %s",
			line
		);

		if (hasControl) {
			controlHandler.accept(envelope.get("control"));
		} else {
			processPacket(envelope.get("packet").asText());
		}
	}

	private void processPacket(String packet) throws Exception {
		byte[] payloadBytes = Base64.getDecoder().decode(packet);

		Preconditions.checkArgument(
			payloadBytes.length >= HEADER_SIZE,
			"Expected at least %s bytes for header, got %s",
			HEADER_SIZE,
			payloadBytes.length
		);

		byte[] headerBytes = Arrays.copyOfRange(payloadBytes, 0, HEADER_SIZE);
		MessageHeader header = headerDeserializer.deserialize(headerBytes);

		int bodyLength = payloadBytes.length - HEADER_SIZE;
		Preconditions.checkArgument(
			bodyLength == header.payloadLength(),
			"Header payloadLength is %s but body is %s bytes",
			header.payloadLength(),
			bodyLength
		);

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
		// Closing the reader here would block: BufferedReader.close() waits for the
		// lock that a blocked readLine() holds. The reading thread instead exits on
		// the next line or at end of input, or when the process exits.
		stopping = true;
	}

	@Override
	protected void shutDown() throws Exception {
		bufferedReader.close();
	}
}
