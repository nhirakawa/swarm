package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.google.common.util.concurrent.AbstractIdleService;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Talks to the router over stdin/stdout: protocol messages travel as {@code packet}
 * lines, and the router's control messages as {@code control} lines.
 */
public class StdioTransport
	extends AbstractIdleService
	implements SwarmTransport
{

	private static final Logger LOG = LogManager.getLogger(StdioTransport.class);

	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration CONTROL_SEND_TIMEOUT = Duration.ofSeconds(1);

	private final StdioReceiver receiver;
	private final StdioSender sender;

	private volatile Supplier<StateSnapshot> snapshotSupplier = () -> null;
	private volatile Runnable endOfInputListener = () -> {};

	public StdioTransport(
		StdioAddress localAddress,
		ObjectWriter objectWriter,
		ObjectReader objectReader
	) {
		this.receiver = new StdioReceiver(
			objectReader,
			this::handleControl,
			() -> endOfInputListener.run()
		);
		this.sender = new StdioSender(localAddress, objectWriter);
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
		return StdioAddress.BROADCAST;
	}

	/** Supplies the snapshot sent in reply to the router's {@code status_request}. */
	public void setSnapshotSupplier(Supplier<StateSnapshot> snapshotSupplier) {
		this.snapshotSupplier = snapshotSupplier;
	}

	/** Runs when stdin closes, which means the router is gone. */
	public void setEndOfInputListener(Runnable endOfInputListener) {
		this.endOfInputListener = endOfInputListener;
	}

	/** Tells the router that this node can accept protocol messages. Send once. */
	public void sendReady() throws InterruptedException {
		sendControl(Map.of("kind", "ready"));
	}

	private void handleControl(JsonNode control) {
		String kind = control.path("kind").asText();
		switch (kind) {
			case "status_request" -> replyWithStatus(control.path("id").asLong());
			default -> LOG.debug("Ignoring control message of kind '{}'", kind);
		}
	}

	private void replyWithStatus(long id) {
		StateSnapshot snapshot = snapshotSupplier.get();
		if (snapshot == null) {
			LOG.warn("No snapshot available for status_request {}", id);
			return;
		}

		try {
			sendControl(
				Map.of("kind", "status", "id", id, "snapshot", toSnapshotMap(snapshot))
			);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void sendControl(Map<String, Object> control)
		throws InterruptedException {
		if (!sender.sendControl(control, CONTROL_SEND_TIMEOUT)) {
			LOG.warn("Timed out queueing control message {}", control.get("kind"));
		}
	}

	private static Map<String, Object> toSnapshotMap(StateSnapshot snapshot) {
		return Map.of(
			"localAddress",
			snapshot.getLocalAddress().asString(),
			"protocolPeriodId",
			snapshot.getProtocolPeriodId(),
			"incarnation",
			snapshot.getIncarnation(),
			"memberStatuses",
			snapshot.getMemberStatuses()
		);
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
		// The receiver may be blocked reading stdin, which cannot be interrupted, so
		// don't wait for it to terminate.
		receiver.stopAsync();
	}
}
