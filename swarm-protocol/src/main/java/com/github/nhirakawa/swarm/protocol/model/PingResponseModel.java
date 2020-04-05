package com.github.nhirakawa.swarm.protocol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface PingResponseModel extends BaseSwarmMessage {
  SwarmNode getLocalSwarmNode();
  SwarmNode getTargetNode();

  @JsonIgnore
  @Value.Derived
  default SwarmNode getSender() {
    return getLocalSwarmNode();
  }
}
