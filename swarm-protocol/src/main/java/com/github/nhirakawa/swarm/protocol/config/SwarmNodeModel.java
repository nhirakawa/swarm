package com.github.nhirakawa.swarm.protocol.config;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;
import java.util.UUID;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public interface SwarmNodeModel {
  UUID getUniqueId();
  String getHost();
  int getPort();
}
