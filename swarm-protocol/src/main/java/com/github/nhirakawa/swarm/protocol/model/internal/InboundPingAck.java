package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

public record InboundPingAck(
  SwarmAddress from,
  Optional<SwarmAddress> proxyFor,
  String protocolPeriodId
)
  implements StateMachineMessage {}
