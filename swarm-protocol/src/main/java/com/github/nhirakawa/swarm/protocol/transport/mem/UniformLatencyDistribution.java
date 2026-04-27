package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.google.common.base.Preconditions;
import java.time.Duration;
import org.apache.commons.math3.distribution.UniformRealDistribution;

public class UniformLatencyDistribution implements LatencyDistribution {

  private final UniformRealDistribution distribution;

  public UniformLatencyDistribution(Duration min, Duration max) {
    Preconditions.checkArgument(!min.isNegative(), "min must be non-negative");
    Preconditions.checkArgument(min.compareTo(max) < 0, "min must be strictly less than max");
    this.distribution = new UniformRealDistribution(min.toNanos(), max.toNanos());
  }

  @Override
  public synchronized Duration sample() {
    return Duration.ofNanos((long) distribution.sample());
  }
}
