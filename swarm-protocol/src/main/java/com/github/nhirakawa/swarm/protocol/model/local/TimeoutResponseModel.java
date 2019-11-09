package com.github.nhirakawa.swarm.protocol.model.local;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;

@Value.Immutable
@ImmutableStyle
public interface TimeoutResponseModel {

  Optional<SwarmNode> getTargetNode();

}
