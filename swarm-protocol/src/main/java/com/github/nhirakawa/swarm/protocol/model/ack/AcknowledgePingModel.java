package com.github.nhirakawa.swarm.protocol.model.ack;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class AcknowledgePingModel extends PingAck {

  public abstract Instant getTimestamp();
}
