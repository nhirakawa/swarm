package com.github.nhirakawa.swarm.protocol.config;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.base.Preconditions;
import java.time.Duration;
import org.immutables.value.Value;
import org.jspecify.annotations.NonNull;

@Value.Immutable
@ImmutableStyle
public abstract class SwarmConfigModel {

  @NonNull
  public abstract SwarmAddress getLocalAddress();

  @NonNull
  public abstract SwarmAddress getMulticastAddress();

  public abstract Duration getProtocolPeriod();

  public abstract Duration getMessageTimeout();

  public abstract Duration getProtocolTick();

  public abstract int getFailureSubGroup();

  @Value.Default
  public Duration getSuspicionTimeout() {
    return Duration.ofMillis(getProtocolPeriod().toMillis() * 3);
  }

  @Value.Default
  public Duration getProtocolPeriodJitter() {
    return Duration.ofMillis(getProtocolPeriod().toMillis() / 10);
  }

  @Value.Default
  public Duration getMessageTimeoutJitter() {
    return Duration.ofMillis(getMessageTimeout().toMillis() / 10);
  }

  @Value.Default
  public boolean isDiscoveryEnabled() {
    return true;
  }

  @Value.Default
  public Duration getDiscoveryInitialRetryDelay() {
    return Duration.ofMillis(500);
  }

  @Value.Default
  public Duration getDiscoveryMaxRetryDelay() {
    return Duration.ofSeconds(30);
  }

  @Value.Default
  public double getDiscoveryBackoffMultiplier() {
    return 2.0;
  }

  @Value.Check
  public void check() {
    Preconditions.checkArgument(
      getProtocolPeriod().compareTo(getMessageTimeout()) > 0,
      "Expected protocol period (%s) to be longer than message timeout (%s)",
      getProtocolPeriod(),
      getMessageTimeout()
    );

    Preconditions.checkArgument(
      getDiscoveryInitialRetryDelay().compareTo(Duration.ZERO) > 0,
      "Discovery initial retry delay must be positive: %s",
      getDiscoveryInitialRetryDelay()
    );

    Preconditions.checkArgument(
      getDiscoveryMaxRetryDelay().compareTo(getDiscoveryInitialRetryDelay()) >= 0,
      "Discovery max retry delay (%s) must be >= initial retry delay (%s)",
      getDiscoveryMaxRetryDelay(),
      getDiscoveryInitialRetryDelay()
    );

    Preconditions.checkArgument(
      getDiscoveryBackoffMultiplier() >= 1.0,
      "Discovery backoff multiplier must be >= 1.0: %s",
      getDiscoveryBackoffMultiplier()
    );
  }
}
