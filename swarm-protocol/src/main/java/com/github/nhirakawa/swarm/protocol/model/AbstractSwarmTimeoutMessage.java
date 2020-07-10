package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class AbstractSwarmTimeoutMessage {

  public abstract Instant getTimestamp();
}
