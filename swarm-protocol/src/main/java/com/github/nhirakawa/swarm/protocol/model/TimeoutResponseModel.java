package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface TimeoutResponseModel {
  Optional<SwarmNode> getTargetNode();
}
