package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import java.time.Instant;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface SwarmStateModel {
  Instant getTimestamp();
  Instant getLastProtocolPeriodStarted();
  Map<SwarmNode, Instant> getLastAckRequestBySwarmNode();
}
