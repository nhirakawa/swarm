package com.github.nhirakawa.swarm.protocol.state;

import com.google.common.base.Ticker;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Counts how often this node refutes its own confirmation within a sliding window.
 * <p>
 * A node that is repeatedly confirmed dead cannot keep up with its peers, so past a
 * limit it should stop rather than keep flapping between ALIVE and CONFIRMED.
 */
@NotThreadSafe
final class RefutationTracker {

	private final Ticker ticker;
	private final Deque<Long> refutationTimesNanos = new ArrayDeque<>();

	RefutationTracker() {
		this(Ticker.systemTicker());
	}

	RefutationTracker(Ticker ticker) {
		this.ticker = ticker;
	}

	/**
	 * Records a refutation and reports whether more than {@code maxRefutations} have
	 * happened within {@code window}, counting this one.
	 */
	boolean recordAndCheckExceeded(int maxRefutations, Duration window) {
		long now = ticker.read();
		refutationTimesNanos.addLast(now);

		long windowStart = now - window.toNanos();
		while (refutationTimesNanos.peekFirst() < windowStart) {
			refutationTimesNanos.removeFirst();
		}

		return refutationTimesNanos.size() > maxRefutations;
	}
}
