package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;

public record InboundDiscoveryRequest(SwarmAddress from)
  implements StateMachineMessage {}
