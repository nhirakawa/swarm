package com.github.nhirakawa.swarm.protocol.model.ack;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;

import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class AcknowledgeProxyModel extends PingAck {

  public abstract SwarmNode getProxyFor();
}
