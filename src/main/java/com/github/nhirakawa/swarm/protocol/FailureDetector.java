package com.github.nhirakawa.swarm.protocol;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import javax.inject.Inject;

public class FailureDetector {

  private final Duration protocolPeriod;
  private final int failureSubgroupSize;

  @Inject
  FailureDetector() {
    this.protocolPeriod = Duration.of(10L, ChronoUnit.SECONDS);
    this.failureSubgroupSize = 3;
  }
}
