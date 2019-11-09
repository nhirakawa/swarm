package com.github.nhirakawa.swarm.protocol.model.local;

import java.time.Instant;

import org.immutables.value.Value;

import com.github.nhirakawa.immutable.style.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
public interface PingAckResponseModel {

  Instant getTimestamp();

}
