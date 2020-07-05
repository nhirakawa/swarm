package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface AbstractPingMessage extends BaseSwarmMessage {
  @Override
  @Value.Auxiliary
  default SwarmMessageType getType() {
    return SwarmMessageType.PING;
  }
}
