package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.SwarmTerminationCallback;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

final class ProtocolStateContext {

	private final SwarmConfig swarmConfig;
	private final long protocolPeriodId;
	// AtomicLong is used here (interior mutability) so that a node can refute its
	// own suspicion by bumping its incarnation mid-period without needing to
	// reconstruct every state in the chain — all contexts derived via next() share
	// the same AtomicLong instance.
	private final AtomicLong incarnation;
	private final Stopwatch stopwatch;
	private final MemberRegistry memberRegistry;
	private final SwarmTerminationCallback terminationCallback;
	// Like incarnation, these are shared by every context derived via next().
	private final RefutationTracker refutationTracker;
	private final Stopwatch rediscoveryStopwatch;

	ProtocolStateContext(
		SwarmConfig swarmConfig,
		long protocolPeriodId,
		long incarnation,
		Stopwatch stopwatch,
		MemberRegistry memberRegistry,
		SwarmTerminationCallback terminationCallback
	) {
		this(
			swarmConfig,
			protocolPeriodId,
			incarnation,
			stopwatch,
			memberRegistry,
			terminationCallback,
			new RefutationTracker(),
			Stopwatch.createStarted()
		);
	}

	ProtocolStateContext(
		SwarmConfig swarmConfig,
		long protocolPeriodId,
		long incarnation,
		Stopwatch stopwatch,
		MemberRegistry memberRegistry,
		SwarmTerminationCallback terminationCallback,
		RefutationTracker refutationTracker,
		Stopwatch rediscoveryStopwatch
	) {
		this(
			swarmConfig,
			protocolPeriodId,
			new AtomicLong(incarnation),
			stopwatch,
			memberRegistry,
			terminationCallback,
			refutationTracker,
			rediscoveryStopwatch
		);
	}

	private ProtocolStateContext(
		SwarmConfig swarmConfig,
		long protocolPeriodId,
		AtomicLong incarnation,
		Stopwatch stopwatch,
		MemberRegistry memberRegistry,
		SwarmTerminationCallback terminationCallback,
		RefutationTracker refutationTracker,
		Stopwatch rediscoveryStopwatch
	) {
		this.swarmConfig = swarmConfig;
		this.protocolPeriodId = protocolPeriodId;
		this.incarnation = incarnation;
		this.stopwatch = stopwatch;
		this.memberRegistry = memberRegistry;
		this.terminationCallback = terminationCallback;
		this.refutationTracker = refutationTracker;
		this.rediscoveryStopwatch = rediscoveryStopwatch;
	}

	ProtocolStateContext next() {
		return new ProtocolStateContext(
			swarmConfig,
			ThreadLocalRandom.current().nextLong(),
			incarnation,
			stopwatch.reset().start(),
			memberRegistry,
			terminationCallback,
			refutationTracker,
			rediscoveryStopwatch
		);
	}

	SwarmConfig swarmConfig() {
		return swarmConfig;
	}

	long protocolPeriodId() {
		return protocolPeriodId;
	}

	long incarnation() {
		return incarnation.get();
	}

	/**
	 * Raises this node's incarnation above {@code accusedIncarnation}, so that its
	 * ALIVE status overrides the SUSPECTED or CONFIRMED status it is refuting.
	 */
	void refuteAbove(long accusedIncarnation) {
		incarnation.updateAndGet(current ->
			Math.max(current, accusedIncarnation) + 1
		);
	}

	Duration elapsed() {
		return stopwatch.elapsed();
	}

	MemberRegistry memberRegistry() {
		return memberRegistry;
	}

	SwarmTerminationCallback terminationCallback() {
		return terminationCallback;
	}

	RefutationTracker refutationTracker() {
		return refutationTracker;
	}

	Stopwatch rediscoveryStopwatch() {
		return rediscoveryStopwatch;
	}
}
