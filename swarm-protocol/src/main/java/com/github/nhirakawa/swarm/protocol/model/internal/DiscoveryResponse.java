package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.state.MemberStatus;
import java.util.List;
import java.util.Optional;

public record DiscoveryResponse(
  SwarmAddress source,
	SwarmAddress target,
  List<MemberStatus> memberList
) implements StateMachineMessage {
	@Override
	public Optional<SwarmAddress> proxyFor() {
		return Optional.empty();
	}

	@Override
	public SwarmMessageType type() {
		return SwarmMessageType.DISCOVERY_RESPONSE;
	}
}
