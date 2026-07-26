package com.github.nhirakawa.swarm.protocol.transport.stdio;

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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class StdioSender
	extends AbstractExecutionThreadService
	implements SwarmMessageSender
{

	private final BufferedWriter bufferedWriter;
	private final BlockingQueue<StateMachineMessage> queue;
	private final ObjectWriter objectWriter;
	private final HeaderSerializer headerSerializer;
	private final AtomicLong messageIdCounter;

	StdioSender(ObjectWriter objectWriter) {
		this(System.out, objectWriter);
	}

	@VisibleForTesting
	StdioSender(OutputStream outputStream, ObjectWriter objectWriter) {
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
			StateMachineMessage message = queue.poll(100, TimeUnit.MILLISECONDS);
			if (message == null) {
				continue;
			}
			sendMessage(message);
		}
	}

	private void sendMessage(StateMachineMessage message) throws IOException {
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

		String payloadBase64 = Base64.getEncoder().encodeToString(payload);
		Envelope envelope = new Envelope(
			message.source().asString(),
			message.target().asString(),
			payloadBase64
		);

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
		return queue.offer(message, timeout.toNanos(), TimeUnit.NANOSECONDS);
	}

	private record Envelope(
		@JsonProperty("source") String source,
		@JsonProperty("target") String target,
		@JsonProperty("payload") String payload
	) {}
}
