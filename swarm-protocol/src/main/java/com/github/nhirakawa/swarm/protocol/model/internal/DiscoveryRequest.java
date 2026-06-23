package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;

import java.util.Optional;

public record DiscoveryRequest(SwarmAddress source, SwarmAddress target)
  implements StateMachineMessage {
	@Override
	public Optional<SwarmAddress> proxyFor() {
		return Optional.empty();
	}

	@Override
	public SwarmMessageType type() {
		return SwarmMessageType.DISCOVERY_REQUEST;
	}
}
