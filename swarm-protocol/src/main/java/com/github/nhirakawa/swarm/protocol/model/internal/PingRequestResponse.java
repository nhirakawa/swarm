package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

public record PingRequestResponse(
  SwarmAddress target,
  Optional<SwarmAddress> onBehalfOf,
  String protocolPeriodId
)
  implements StateMachineResponse {}
