package com.github.nhirakawa.swarm.protocol.model;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public abstract class AbstractPingRequestMessage implements BaseSwarmMessage {

  @Override
  @Value.Auxiliary
  @JsonIgnore
  public SwarmMessageType getType() {
    return SwarmMessageType.PING_REQUEST;
  }
}
