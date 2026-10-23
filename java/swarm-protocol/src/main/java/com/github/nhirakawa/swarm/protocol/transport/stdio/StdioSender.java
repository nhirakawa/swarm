package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.serde.HeaderSerializer;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class StdioSender
	extends AbstractExecutionThreadService
	implements SwarmMessageSender
{

	private final StdioAddress localAddress;
	private final BufferedWriter bufferedWriter;
	private final BlockingQueue<Outbound> queue;
	private final ObjectWriter objectWriter;
	private final HeaderSerializer headerSerializer;
	private final AtomicLong messageIdCounter;

	StdioSender(StdioAddress localAddress, ObjectWriter objectWriter) {
		this(localAddress, System.out, objectWriter);
	}

	@VisibleForTesting
	StdioSender(
		StdioAddress localAddress,
		OutputStream outputStream,
		ObjectWriter objectWriter
	) {
		this.localAddress = localAddress;
		this.bufferedWriter = new BufferedWriter(
			new OutputStreamWriter(outputStream)
		);
		this.queue = new ArrayBlockingQueue<>(100);
		this.objectWriter = objectWriter;
		this.headerSerializer = new HeaderSerializer();
		this.messageIdCounter = new AtomicLong(0);
	}

	@Override
	protected void run() throws Exception {
		while (isRunning()) {
			Outbound outbound = queue.poll(100, TimeUnit.MILLISECONDS);
			switch (outbound) {
				case null -> {}
				case Packet(StateMachineMessage message) -> writeLine(
					toPacketEnvelope(message)
				);
				case Control(Map<String, Object> control) -> writeLine(
					new Envelope(
						localAddress.asString(),
						StdioAddress.ROUTER.asString(),
						null,
						control
					)
				);
			}
		}
	}

	private Envelope toPacketEnvelope(StateMachineMessage message)
		throws IOException {
		byte[] bodyBytes = objectWriter.writeValueAsBytes(message);
		byte[] headerBytes = headerSerializer.serialize(
			createHeader(message, bodyBytes.length)
		);

		byte[] payload = new byte[headerBytes.length + bodyBytes.length];
		System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
		System.arraycopy(
			bodyBytes,
			0,
			payload,
			headerBytes.length,
			bodyBytes.length
		);

		return new Envelope(
			message.source().asString(),
			message.target().asString(),
			Base64.getEncoder().encodeToString(payload),
			null
		);
	}

	private void writeLine(Envelope envelope) throws IOException {
		bufferedWriter.write(objectWriter.writeValueAsString(envelope));
		bufferedWriter.newLine();
		bufferedWriter.flush();
	}

	private MessageHeader createHeader(
		StateMachineMessage message,
		int payloadLength
	) {
		return new MessageHeader.Builder()
			.messageVersion(MessageVersion.V0)
			.type(message.type())
			.compression(Compression.NONE)
			.serialization(Serialization.JSON)
			.payloadLength(payloadLength)
			.messageId(messageIdCounter.incrementAndGet())
			.timestamp(System.currentTimeMillis())
			.checksum(0)
			.build();
	}

	@Override
	protected void shutDown() throws Exception {
		bufferedWriter.close();
	}

	@Override
	public boolean send(StateMachineMessage message, Duration timeout)
		throws TimeoutException, InterruptedException {
		return queue.offer(
			new Packet(message),
			timeout.toNanos(),
			TimeUnit.NANOSECONDS
		);
	}

	/**
	 * Queues a control message for the router. {@code control} must include its
	 * {@code kind}.
	 */
	boolean sendControl(Map<String, Object> control, Duration timeout)
		throws InterruptedException {
		return queue.offer(
			new Control(control),
			timeout.toNanos(),
			TimeUnit.NANOSECONDS
		);
	}

	private sealed interface Outbound permits Packet, Control {}

	private record Packet(StateMachineMessage message) implements Outbound {}

	private record Control(Map<String, Object> control) implements Outbound {}

	/** One line on stdout. Exactly one of {@code packet} and {@code control} is set. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record Envelope(
		@JsonProperty("source") String source,
		@JsonProperty("target") String target,
		@JsonProperty("packet") String packet,
		@JsonProperty("control") Map<String, Object> control
	) {}
}
