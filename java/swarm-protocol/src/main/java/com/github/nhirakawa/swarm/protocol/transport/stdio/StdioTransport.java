package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.google.common.util.concurrent.AbstractIdleService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class StdioTransport
	extends AbstractIdleService
	implements SwarmTransport
{

	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

	private final StdioReceiver receiver;
	private final StdioSender sender;

	public StdioTransport(ObjectWriter objectWriter, ObjectReader objectReader) {
		this.receiver = new StdioReceiver(objectReader);
		this.sender = new StdioSender(objectWriter);
	}

	@Override
	public SwarmMessageReceiver receiver() {
		return receiver;
	}

	@Override
	public SwarmMessageSender sender() {
		return sender;
	}

	@Override
	public SwarmAddress getMulticastAddress() {
		return new StdioAddress("MULTICAST");
	}

	@Override
	protected void startUp() throws Exception {
		receiver
			.startAsync()
			.awaitRunning(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		sender
			.startAsync()
			.awaitRunning(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	protected void shutDown() throws Exception {
		sender
			.stopAsync()
			.awaitTerminated(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		receiver
			.stopAsync()
			.awaitTerminated(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
	}
}
