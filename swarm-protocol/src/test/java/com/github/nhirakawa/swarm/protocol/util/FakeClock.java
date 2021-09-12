package com.github.nhirakawa.swarm.protocol.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

public class FakeClock extends Clock {
  private final AtomicLong timestamp;

  FakeClock() {
    this(1000L);
  }

  FakeClock(long initialTimestamp) {
    this.timestamp = new AtomicLong(initialTimestamp);
  }

  @Override
  public ZoneId getZone() {
    return ZoneId.systemDefault();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(timestamp.addAndGet(100));
  }
}
