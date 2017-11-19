package com.github.nhirakawa.swarm.config;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize
public interface SwarmNode {

  String getHost();
  int getPort();

  @Value.Lazy
  @JsonIgnore
  default SocketAddress getSocketAddress() {
    return new InetSocketAddress(getHost(), getPort());
  }

}
