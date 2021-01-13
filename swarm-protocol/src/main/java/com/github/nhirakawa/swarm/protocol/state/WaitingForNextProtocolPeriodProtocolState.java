package com.github.nhirakawa.swarm.protocol.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingRequestMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.protocol.Transition;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterables;

public class WaitingForNextProtocolPeriodProtocolState
  extends SwarmProtocolState {
  private static final Logger LOG = LoggerFactory.getLogger(
    WaitingForNextProtocolPeriodProtocolState.class
  );

  protected WaitingForNextProtocolPeriodProtocolState(
    Instant protocolStartTimestamp,
    SwarmConfig swarmConfig,
    String protocolPeriodId
  ) {
    super(protocolStartTimestamp, swarmConfig, protocolPeriodId);
  }

  @Override
  public Optional<Transition> applyTick(
    SwarmTimeoutMessage swarmTimeoutMessage
  ) {
    Duration sinceStart = Duration.between(
      protocolStartTimestamp,
      swarmTimeoutMessage.getTimestamp()
    );

    if (sinceStart.compareTo(swarmConfig.getProtocolPeriod()) <= 0) {
      return Optional.empty();
    }

    SwarmNode pingTarget = Iterables.getOnlyElement(
      getRandomNodes(1, Optional.empty())
    );

    SwarmProtocolState newState = new WaitingForAckProtocolState(
      swarmTimeoutMessage.getTimestamp(),
      swarmConfig,
      pingTarget,
      UUID.randomUUID().toString()
    );

    PingRequestMessage pingRequestMessage = PingRequestMessage
      .builder()
      .setProtocolPeriodId(protocolPeriodId)
      .setUniqueMessageId(UUID.randomUUID().toString())
      .setFrom(swarmConfig.getLocalNode())
      .setTo(pingTarget)
      .build();

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(newState)
      .addMessagesToSend(pingRequestMessage)
      .build();

    return Optional.of(transition);
  }

  @Override
  public Optional<Transition> applyPingAck(PingAckMessage pingAckMessage) {
    LOG.trace("Ignoring {}", pingAckMessage);
    return Optional.empty();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("protocolStartTimestamp", protocolStartTimestamp)
        .add("swarmConfig", swarmConfig)
        .add("protocolPeriodId", protocolPeriodId)
        .add("clusterNodesList", clusterNodesList)
        .toString();
  }

}
