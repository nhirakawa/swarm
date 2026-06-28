package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

final class ProtocolStateContext {

	private final SwarmConfig swarmConfig;
	private final long protocolPeriodId;
	private final long incarnation;
	private final Stopwatch stopwatch;
	private final MemberRegistry memberRegistry;

	ProtocolStateContext(
			SwarmConfig swarmConfig,
			long protocolPeriodId,
			long incarnation,
			Stopwatch stopwatch,
			MemberRegistry memberRegistry
	) {
		this.swarmConfig = swarmConfig;
		this.protocolPeriodId = protocolPeriodId;
		this.incarnation = incarnation;
		this.stopwatch = stopwatch;
		this.memberRegistry = memberRegistry;
	}

	ProtocolStateContext next() {
		return new ProtocolStateContext(
				swarmConfig,
				ThreadLocalRandom.current().nextLong(),
				incarnation,
				stopwatch.reset().start(),
				memberRegistry
		);
	}

	SwarmConfig swarmConfig() {
		return swarmConfig;
	}

	long protocolPeriodId() {
		return protocolPeriodId;
	}

	long incarnation() {
		return incarnation;
	}

	Duration elapsed() {
		return stopwatch.elapsed();
	}

	MemberRegistry memberRegistry() {
		return memberRegistry;
	}
}
