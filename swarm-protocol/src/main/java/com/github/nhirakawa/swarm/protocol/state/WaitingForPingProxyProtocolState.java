package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.protocol.Transition;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WaitingForPingProxyProtocolState extends SwarmProtocolState {
  private static final Logger LOG = LoggerFactory.getLogger(
    WaitingForPingProxyProtocolState.class
  );

  private final SwarmNode pingTarget;
  private final Set<SwarmNode> proxyTargets;

  @Inject
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
    return Optional.empty();
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
      Transition.builder().setNextSwarmProtocolState(nextState).build()
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
      .add("swarmConfig", swarmConfig)
      .add("protocolPeriodId", protocolPeriodId)
      .add("clusterNodesList", clusterNodesList)
      .add("pingTarget", pingTarget)
      .add("proxyTargets", proxyTargets)
      .toString();
  }
}
