package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface SwarmStateModel {
  Instant getTimestamp();
  Instant getLastProtocolPeriodStarted();
}
