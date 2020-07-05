package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.google.common.base.Preconditions;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface TimeoutResponseModel {
  Optional<SwarmNode> getTargetNode();
  Optional<SwarmNode> getProxyNode();

  @Value.Check
  default void check() {
    if (getProxyNode().isPresent()) {
      Preconditions.checkArgument(
        getTargetNode().isPresent(),
        "Must set target node when proxy node is set"
      );
    }
  }
}
