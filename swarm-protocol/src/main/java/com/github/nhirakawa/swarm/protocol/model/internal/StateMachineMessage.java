package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;

import java.util.Optional;

public sealed interface StateMachineMessage
  permits PingAck,
		PingRequest,
		DiscoveryRequest,
		DiscoveryResponse {
	SwarmAddress source();
	SwarmAddress target();
	Optional<SwarmAddress> proxyFor();
	SwarmMessageType type();
}
