package com.github.nhirakawa.swarm.model;

import java.time.Instant;

import org.immutables.value.Value;

import com.github.nhirakawa.immutable.style.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
public abstract class AbstractSwarmTimeoutMessage {

  public abstract Instant getTImestamp();

}
