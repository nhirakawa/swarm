package com.github.nhirakawa.swarm.protocol.model.local;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface PingAckResponseModel {
  Instant getTimestamp();
}
