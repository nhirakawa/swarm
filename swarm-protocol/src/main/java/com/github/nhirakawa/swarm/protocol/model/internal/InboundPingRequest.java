package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

public record InboundPingRequest(
  SwarmAddress from,
  Optional<SwarmAddress> onBehalfOf,
  String protocolPeriodId
)
  implements StateMachineMessage {}
