package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.state.MemberStatus;

import java.util.List;
import java.util.Optional;

public record PingAck(
  SwarmAddress source,
	SwarmAddress target,
  Optional<SwarmAddress> proxyFor,
  long protocolPeriodId,
  long incarnation,
  List<MemberStatus> gossip
)
  implements StateMachineMessage {
	@Override
	public SwarmMessageType type() {
		return SwarmMessageType.PING_ACK;
	}
}
