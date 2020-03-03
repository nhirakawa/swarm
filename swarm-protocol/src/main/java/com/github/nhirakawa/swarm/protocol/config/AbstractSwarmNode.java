package com.github.nhirakawa.swarm.protocol.config;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.address.Address;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public interface AbstractSwarmNode {
  String getUniqueId();
  Address getAddress();
}
