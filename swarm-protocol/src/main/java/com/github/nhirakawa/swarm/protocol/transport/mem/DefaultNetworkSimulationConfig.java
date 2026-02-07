package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.util.JitterUtil;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default network simulation configuration with uniform latency and drop rates.
 */
public class DefaultNetworkSimulationConfig
  implements NetworkSimulationConfig {

  private final Duration baseLatency;
  private final Duration latencyJitter;
  private final double dropRate;

  public DefaultNetworkSimulationConfig(
    Duration baseLatency,
    Duration latencyJitter,
    double dropRate
  ) {
    if (dropRate < 0.0 || dropRate > 1.0) {
      throw new IllegalArgumentException(
        "dropRate must be between 0.0 and 1.0, got: " + dropRate
      );
    }
    this.baseLatency = baseLatency;
    this.latencyJitter = latencyJitter;
    this.dropRate = dropRate;
  }

  /**
   * Create a config with no latency and no packet loss (perfect network).
   */
  public static DefaultNetworkSimulationConfig perfect() {
    return new DefaultNetworkSimulationConfig(
      Duration.ZERO,
      Duration.ZERO,
      0.0
    );
  }

  @Override
  public Duration sampleLatency(SwarmAddress source, SwarmAddress target) {
    return JitterUtil.applyJitter(baseLatency, latencyJitter);
  }

  @Override
  public boolean shouldDropOnSend(SwarmAddress source, SwarmAddress target) {
    return shouldDrop();
  }

  @Override
  public boolean shouldDropInTransit(SwarmAddress source, SwarmAddress target) {
    return shouldDrop();
  }

  private boolean shouldDrop() {
    return ThreadLocalRandom.current().nextDouble() < dropRate;
  }
}
