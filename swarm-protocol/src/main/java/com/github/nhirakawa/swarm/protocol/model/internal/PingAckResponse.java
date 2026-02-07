package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

public record PingAckResponse(
  SwarmAddress target,
  Optional<SwarmAddress> proxyFor,
  long protocolPeriodId
)
  implements StateMachineResponse {}
