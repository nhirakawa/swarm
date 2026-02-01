package com.github.nhirakawa.swarm.protocol.config;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class SwarmConfigModel {

  public abstract SwarmAddress getLocalAddress();

  public abstract Set<SwarmAddress> getInitialClusterMembership();

  public abstract Duration getProtocolPeriod();

  public abstract Duration getMessageTimeout();

  public abstract Duration getProtocolTick();

  public abstract int getFailureSubGroup();

  @Value.Default
  public Duration getProtocolPeriodJitter() {
    return Duration.ofMillis(getProtocolPeriod().toMillis() / 10);
  }

  @Value.Default
  public Duration getMessageTimeoutJitter() {
    return Duration.ofMillis(getMessageTimeout().toMillis() / 10);
  }

  @Value.Check
  public void check() {
    Preconditions.checkArgument(
      getProtocolPeriod().compareTo(getMessageTimeout()) > 0,
      "Expected protocol period (%s) to be longer than message timeout (%s)",
      getProtocolPeriod(),
      getMessageTimeout()
    );
  }
}
