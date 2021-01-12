package com.github.nhirakawa.swarm.protocol.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public interface SwarmNodeModel {
  String getHost();
  int getPort();

  @JsonIgnore
  @Value.Derived
  default String getUniqueId() {
    return getHost() + "-" + getPort();
  }
}
