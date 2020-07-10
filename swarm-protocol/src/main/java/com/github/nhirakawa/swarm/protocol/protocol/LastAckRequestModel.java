package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface LastAckRequestModel {
  SwarmNode getSwarmNode();
  Instant getTimestamp();
  String getProtocolPeriodId();
}
