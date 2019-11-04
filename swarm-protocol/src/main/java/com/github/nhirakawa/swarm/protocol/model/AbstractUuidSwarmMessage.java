package com.github.nhirakawa.swarm.protocol.model;

import java.util.UUID;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public interface AbstractUuidSwarmMessage extends BaseSwarmMessage {

  UUID getUuid();

  @Override
  @Value.Auxiliary
  @JsonIgnore
  default SwarmMessageType getType() {
    return SwarmMessageType.UUID;
  }
}
