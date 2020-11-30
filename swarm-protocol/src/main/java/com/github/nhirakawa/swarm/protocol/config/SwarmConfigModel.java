package com.github.nhirakawa.swarm.protocol.config;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import java.time.Duration;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class SwarmConfigModel {

  public abstract Duration getProtocolPeriod();

  public abstract Duration getMessageTimeout();

  public abstract Duration getProtocolTick();

  public abstract int getFailureSubGroup();

  public abstract Set<SwarmNode> getClusterNodes();

  public abstract SwarmNode getLocalNode();

  public abstract boolean isDebugEnabled();

  public abstract int getFailureInjectionPercent();

  public abstract int getSwarmStateBufferSize();
}
