package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DistributionNetworkSimulationConfig implements NetworkSimulationConfig {

  private final Map<NodePair, LatencyDistribution> latencyDistributions;
  private final Map<NodePair, Double> dropRates;
  private final LatencyDistribution defaultDistribution;
  private final double defaultDropRate;

  private DistributionNetworkSimulationConfig(Builder builder) {
    this.latencyDistributions = Map.copyOf(builder.latencyDistributions);
    this.dropRates = Map.copyOf(builder.dropRates);
    this.defaultDistribution = builder.defaultDistribution;
    this.defaultDropRate = builder.defaultDropRate;
  }

  @Override
  public Duration sampleLatency(SwarmAddress source, SwarmAddress target) {
    return latencyDistributions
      .getOrDefault(NodePair.of(source, target), defaultDistribution)
      .sample();
  }

  @Override
  public boolean shouldDropOnSend(SwarmAddress source, SwarmAddress target) {
    return shouldDrop(source, target);
  }

  @Override
  public boolean shouldDropInTransit(SwarmAddress source, SwarmAddress target) {
    return shouldDrop(source, target);
  }

  private boolean shouldDrop(SwarmAddress source, SwarmAddress target) {
    double rate = dropRates.getOrDefault(NodePair.of(source, target), defaultDropRate);
    return ThreadLocalRandom.current().nextDouble() < rate;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final Map<NodePair, LatencyDistribution> latencyDistributions = new HashMap<>();
    private final Map<NodePair, Double> dropRates = new HashMap<>();
    private LatencyDistribution defaultDistribution = () -> Duration.ZERO;
    private double defaultDropRate = 0.0;

    public Builder defaultDistribution(LatencyDistribution distribution) {
      this.defaultDistribution = distribution;
      return this;
    }

    public Builder defaultDropRate(double dropRate) {
      Preconditions.checkArgument(
        dropRate >= 0.0 && dropRate <= 1.0,
        "dropRate must be between 0.0 and 1.0, got: %s",
        dropRate
      );
      this.defaultDropRate = dropRate;
      return this;
    }

    public Builder addPair(SwarmAddress a, SwarmAddress b, LatencyDistribution distribution) {
      latencyDistributions.put(NodePair.of(a, b), distribution);
      return this;
    }

    public Builder addPair(
      SwarmAddress a,
      SwarmAddress b,
      LatencyDistribution distribution,
      double dropRate
    ) {
      Preconditions.checkArgument(
        dropRate >= 0.0 && dropRate <= 1.0,
        "dropRate must be between 0.0 and 1.0, got: %s",
        dropRate
      );
      latencyDistributions.put(NodePair.of(a, b), distribution);
      dropRates.put(NodePair.of(a, b), dropRate);
      return this;
    }

    public DistributionNetworkSimulationConfig build() {
      return new DistributionNetworkSimulationConfig(this);
    }
  }
}
