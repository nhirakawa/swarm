package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class SwarmTimeoutMessageModel {

  public abstract Instant getTimestamp();
}
