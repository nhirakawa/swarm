package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.google.common.base.Preconditions;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface AbstractPingAckMessage extends BaseSwarmMessage {
  @Override
  @Value.Auxiliary
  default SwarmMessageType getType() {
    return SwarmMessageType.PING_ACK;
  }

  Optional<SwarmNode> getOnBehalfOf();

  @Value.Check
  default void check() {
    if (getOnBehalfOf().isPresent()) {
      Preconditions.checkArgument(
        !getOnBehalfOf().get().equals(getSender()),
        "Message cannot have same sender and onBehalfOf (%s)",
        getSender().getUniqueId()
      );
    }
  }
}
