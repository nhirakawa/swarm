package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import java.time.Duration;

public class PerfectNetworkSimulationConfig implements NetworkSimulationConfig {

	@Override
	public Duration sampleLatency(SwarmAddress source, SwarmAddress target) {
		return Duration.ZERO;
	}

	@Override
	public boolean shouldDropOnSend(SwarmAddress source, SwarmAddress target) {
		return false;
	}

	@Override
	public boolean shouldDropInTransit(SwarmAddress source, SwarmAddress target) {
		return false;
	}
}
