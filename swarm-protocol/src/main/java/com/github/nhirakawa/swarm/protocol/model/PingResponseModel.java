package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface PingResponseModel {
  SwarmNode getLocalSwarmNode();
  SwarmNode getTargetNode();
}
