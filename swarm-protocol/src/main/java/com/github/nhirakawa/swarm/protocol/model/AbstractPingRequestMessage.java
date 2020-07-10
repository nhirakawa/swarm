package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface AbstractPingRequestMessage extends BaseSwarmMessage {
  @Override
  @Value.Auxiliary
  default SwarmMessageType getType() {
    return SwarmMessageType.PING_REQUEST;
  }

  Optional<SwarmNode> getOnBehalfOf();
}
