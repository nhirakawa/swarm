package com.github.nhirakawa.swarm.protocol.model.timeout;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class PingTimeoutResponseModel implements TimeoutResponse {

  public abstract String getProtocolId();

  public abstract SwarmNode getSwarmNode();
}
