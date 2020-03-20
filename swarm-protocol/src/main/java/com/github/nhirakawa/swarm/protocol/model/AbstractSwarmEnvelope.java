package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface AbstractSwarmEnvelope {
  SwarmNode getToSwarmNode();
  BaseSwarmMessage getBaseSwarmMessage();

  @Value.Derived
  default SwarmNode getFromSwarmNode() {
    return getBaseSwarmMessage().getSender();
  }
}
