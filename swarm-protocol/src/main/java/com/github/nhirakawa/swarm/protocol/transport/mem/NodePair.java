package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;

public record NodePair(SwarmAddress first, SwarmAddress second) {

  public NodePair {
    if (canonicalKey(first).compareTo(canonicalKey(second)) > 0) {
      SwarmAddress tmp = first;
      first = second;
      second = tmp;
    }
  }

  public static NodePair of(SwarmAddress a, SwarmAddress b) {
    return new NodePair(a, b);
  }

  private static String canonicalKey(SwarmAddress address) {
    return address.address() + ":" + address.port() + ":" + address.uid();
  }
}
