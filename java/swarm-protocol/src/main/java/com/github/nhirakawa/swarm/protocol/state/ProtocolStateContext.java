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
			new AtomicLong(incarnation),
			stopwatch,
			memberRegistry,
			terminationCallback
		);
	}

	private ProtocolStateContext(
		SwarmConfig swarmConfig,
		long protocolPeriodId,
		AtomicLong incarnation,
		Stopwatch stopwatch,
		MemberRegistry memberRegistry,
		SwarmTerminationCallback terminationCallback
	) {
		this.swarmConfig = swarmConfig;
		this.protocolPeriodId = protocolPeriodId;
		this.incarnation = incarnation;
		this.stopwatch = stopwatch;
		this.memberRegistry = memberRegistry;
		this.terminationCallback = terminationCallback;
	}

	ProtocolStateContext next() {
		return new ProtocolStateContext(
			swarmConfig,
			ThreadLocalRandom.current().nextLong(),
			incarnation,
			stopwatch.reset().start(),
			memberRegistry,
			terminationCallback
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

	void incrementIncarnation() {
		incarnation.incrementAndGet();
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
}
