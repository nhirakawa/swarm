package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.MemberStatusUpdate;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.protocol.MemberStatus;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// todo(nhirakawa) document this
public class WaitingForPingProxyProtocolState extends SwarmProtocolState {
  private static final Logger LOG = LoggerFactory.getLogger(
    WaitingForPingProxyProtocolState.class
  );

  private final SwarmNode pingTarget;
  private final Set<SwarmNode> proxyTargets;

  protected WaitingForPingProxyProtocolState(
    Instant timestamp,
    SwarmConfig swarmConfig,
    String protocolPeriodId,
    SwarmNode pingTarget,
    Set<SwarmNode> proxyTargets
  ) {
    super(timestamp, swarmConfig, protocolPeriodId);
    this.pingTarget = pingTarget;
    this.proxyTargets = ImmutableSet.copyOf(proxyTargets);
  }

  @Override
  public Optional<Transition> applyTick(
    SwarmTimeoutMessage swarmTimeoutMessage
  ) {
    long millisSinceProtocolStarted = Duration
      .between(protocolStartTimestamp, swarmTimeoutMessage.getTimestamp())
      .toMillis();

    if (
      millisSinceProtocolStarted < swarmConfig.getProtocolPeriod().toMillis()
    ) {
      return Optional.empty();
    }

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(
        new WaitingForNextProtocolPeriodProtocolState(
          protocolStartTimestamp,
          swarmConfig,
          protocolPeriodId
        )
      )
      .setMemberStatusUpdate(
        MemberStatusUpdate
          .builder()
          .setIncarnationNumber(-1)
          .setNewMemberStatus(MemberStatus.SUSPECTED)
          .setSwarmNode(pingTarget)
          .build()
      )
      .build();

    return Optional.of(transition);
  }

  @Override
  public Optional<Transition> applyPingAck(PingAckMessage pingAckMessage) {
    if (pingAckMessage.getProxyFor().isEmpty()) {
      LOG.warn("Expected proxy-for but did not find one - {}", pingAckMessage);

      return Optional.empty();
    }

    if (!pingAckMessage.getProxyFor().get().equals(pingTarget)) {
      LOG.warn(
        "Expected proxy-for to be {} but was {} - {}",
        pingAckMessage.getProxyFor().get(),
        pingTarget,
        pingAckMessage
      );

      return Optional.empty();
    }

    if (!proxyTargets.contains(pingAckMessage.getFrom())) {
      LOG.warn(
        "{} was not one of the expected proxy targets ({}) - {}",
        pingAckMessage.getFrom(),
        proxyTargets,
        pingAckMessage
      );

      return Optional.empty();
    }

    SwarmProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
      protocolStartTimestamp,
      swarmConfig,
      protocolPeriodId
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WaitingForPingProxyProtocolState that = (WaitingForPingProxyProtocolState) o;
    return (
      pingTarget.equals(that.pingTarget) &&
      proxyTargets.equals(that.proxyTargets)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(pingTarget, proxyTargets);
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(this)
      .add("protocolStartTimestamp", protocolStartTimestamp)
      .add("protocolPeriodId", protocolPeriodId)
      .add("pingTarget", pingTarget)
      .add("proxyTargets", proxyTargets)
      .toString();
  }
}
