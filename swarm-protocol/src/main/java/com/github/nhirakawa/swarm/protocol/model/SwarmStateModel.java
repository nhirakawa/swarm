package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.protocol.LastAckRequest;
import com.github.nhirakawa.swarm.protocol.protocol.MemberStatus;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface SwarmStateModel {
  Instant getTimestamp();
  Instant getLastProtocolPeriodStarted();
  String getLastProtocolPeriodId();
  Map<SwarmNode, MemberStatus> getMemberStatusBySwarmNode();
  Optional<LastAckRequest> getLastAckRequest();
  Optional<Instant> getLastProxySentTimestamp();

  @Value.Check
  default void check() {
    if (getLastAckRequest().isPresent()) {
      LastAckRequest lastAckRequest = getLastAckRequest().get();

      Preconditions.checkArgument(
        getLastProtocolPeriodId().equals(lastAckRequest.getProtocolPeriodId()),
        "LastAckRequest has protocol period ID {} but expceted ID {}",
        lastAckRequest.getProtocolPeriodId(),
        getLastProtocolPeriodId()
      );
    }
  }
}
