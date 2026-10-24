package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.base.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Indirect probes this node is running on behalf of other members.
 * <p>
 * When a member cannot reach its ping target directly, it asks this node to ping
 * the target for it. This node pings the target with a fresh ID and, when the
 * target acks that ID, forwards the ack to the requester. Probes the target never
 * acks are forgotten once the requester would have stopped waiting.
 */
@NotThreadSafe
final class PingRelays {

	record Relay(
		SwarmAddress requester,
		SwarmAddress target,
		long requesterPeriodId,
		long startedAtNanos
	) {}

	private final Ticker ticker;
	private final Map<Long, Relay> relaysByPingId = new HashMap<>();

	PingRelays() {
		this(Ticker.systemTicker());
	}

	PingRelays(Ticker ticker) {
		this.ticker = ticker;
	}

	/**
	 * Records a probe of {@code target} for {@code requester} and returns the ID to
	 * ping the target with.
	 */
	long start(
		SwarmAddress requester,
		SwarmAddress target,
		long requesterPeriodId,
		Duration timeout
	) {
		expire(timeout);
		long pingId;
		do {
			pingId = ThreadLocalRandom.current().nextLong();
		} while (relaysByPingId.containsKey(pingId));
		relaysByPingId.put(
			pingId,
			new Relay(requester, target, requesterPeriodId, ticker.read())
		);
		return pingId;
	}

	/**
	 * Removes and returns the probe that an ack from {@code source} with
	 * {@code pingId} answers, if there is one.
	 */
	Optional<Relay> complete(SwarmAddress source, long pingId, Duration timeout) {
		expire(timeout);
		Relay relay = relaysByPingId.get(pingId);
		if (relay == null || !relay.target().equals(source)) {
			return Optional.empty();
		}
		relaysByPingId.remove(pingId);
		return Optional.of(relay);
	}

	int size() {
		return relaysByPingId.size();
	}

	private void expire(Duration timeout) {
		long cutoff = ticker.read() - timeout.toNanos();
		relaysByPingId.values().removeIf(relay -> relay.startedAtNanos() < cutoff);
	}
}
