package com.github.nhirakawa.swarm.protocol.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nhirakawa.immutable.style.ImmutableStyle;
import java.net.InetSocketAddress;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonSerialize
public interface AbstractSwarmNode {
  String getHost();
  int getPort();

  @Value.Lazy
  @JsonIgnore
  default InetSocketAddress getSocketAddress() {
    return new InetSocketAddress(getHost(), getPort());
  }
}
