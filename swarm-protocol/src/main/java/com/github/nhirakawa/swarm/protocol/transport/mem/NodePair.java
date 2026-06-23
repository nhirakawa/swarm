package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;

public record NodePair(SwarmAddress first, SwarmAddress second) {

  public NodePair {
    if (first.asString().compareTo(second.asString()) > 0) {
      SwarmAddress tmp = first;
      first = second;
      second = tmp;
    }
  }

  public static NodePair of(SwarmAddress a, SwarmAddress b) {
    return new NodePair(a, b);
  }
}
