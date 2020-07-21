package com.github.nhirakawa.swarm.protocol.protocol;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

public class FakeClock extends Clock {
  private static final AtomicLong COUNTER = new AtomicLong(10_000_000);

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(COUNTER.addAndGet(100));
  }
}
