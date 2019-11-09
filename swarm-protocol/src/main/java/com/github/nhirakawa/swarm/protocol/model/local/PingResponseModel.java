package com.github.nhirakawa.swarm.protocol.model.local;

import org.immutables.value.Value;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;

@Value.Immutable
@ImmutableStyle
public interface PingResponseModel {

  SwarmNode getLocalSwarmNode();
  SwarmNode getTargetNode();

}
