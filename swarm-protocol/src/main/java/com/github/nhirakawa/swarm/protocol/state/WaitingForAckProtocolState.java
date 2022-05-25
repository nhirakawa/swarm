package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.MemberStatusUpdate;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingRequestMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.protocol.MemberStatus;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// todo(nhirakawa) document this
public class WaitingForAckProtocolState extends SwarmProtocolState {
  private final SwarmNode pingTarget;

  protected WaitingForAckProtocolState(
    Instant timestamp,
    SwarmConfig swarmConfig,
    SwarmNode pingTarget,
    String protocolPeriodId
  ) {
    super(timestamp, swarmConfig, protocolPeriodId);
    this.pingTarget = pingTarget;
  }

  @Override
  public Optional<Transition> applyTick(
    SwarmTimeoutMessage swarmTimeoutMessage
  ) {
    if (
      protocolStartTimestamp
        .plus(swarmConfig.getMessageTimeout())
        .isBefore(swarmTimeoutMessage.getTimestamp())
    ) {
      Set<SwarmNode> failureSubGroup = getRandomNodes(
        swarmConfig.getFailureSubGroup(),
        Optional.of(pingTarget)
      );

      List<BaseSwarmMessage> swarmMessages = failureSubGroup
        .stream()
        .map(
          swarmNode -> PingRequestMessage
            .builder()
            .setProtocolPeriodId(protocolPeriodId)
            .setUniqueMessageId(UUID.randomUUID().toString())
            .setFrom(swarmConfig.getLocalNode())
            .setTo(swarmNode)
            .setOnBehalfOf(pingTarget)
            .build()
        )
        .collect(ImmutableList.toImmutableList());

      SwarmProtocolState nextState = new WaitingForPingProxyProtocolState(
        protocolStartTimestamp,
        swarmConfig,
        protocolPeriodId,
        pingTarget,
        failureSubGroup
      );

      return Optional.of(
        Transition
          .builder()
          .setNextSwarmProtocolState(nextState)
          .addAllMessagesToSend(swarmMessages)
          .build()
      );
    }

    return Optional.empty();
  }

  @Override
  public Optional<Transition> applyPingAck(PingAckMessage pingAckMessage) {
    if (pingAckMessage.getFrom().equals(pingTarget)) {
      WaitingForNextProtocolPeriodProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
        protocolStartTimestamp.plus(swarmConfig.getProtocolPeriod()),
        swarmConfig,
        UUID.randomUUID().toString()
      );

      return Optional.of(
        Transition
          .builder()
          .setNextSwarmProtocolState(nextState)
          .setMemberStatusUpdate(
            MemberStatusUpdate
              .builder()
              .setNewMemberStatus(MemberStatus.ALIVE)
              .setSwarmNode(pingTarget)
              .setIncarnationNumber(1L)
              .build()
          )
          .build()
      );
    }

    return Optional.empty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WaitingForAckProtocolState that = (WaitingForAckProtocolState) o;
    return Objects.equals(pingTarget, that.pingTarget);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pingTarget);
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(this)
      .add("protocolStartTimestamp", protocolStartTimestamp)
      .add("protocolPeriodId", protocolPeriodId)
      .add("pingTarget", pingTarget)
      .toString();
  }
}
