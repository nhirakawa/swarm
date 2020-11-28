package com.github.nhirakawa.swarm.protocol.model.ping;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class PingProxyModel extends PingResponse {

  public abstract SwarmNode getSwarmNode();
}
