package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.swarm.protocol.model.local.TimeoutResponse;

public final class TimeoutResponses {
  private static final TimeoutResponse EMPTY = TimeoutResponse
    .builder()
    .build();

  private TimeoutResponses() {
    throw new IllegalStateException("Cannot instantiate TimeoutResponses");
  }

  public static TimeoutResponse empty() {
    return EMPTY;
  }
}
