package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.util.Jitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

// todo(nhirakawa) document this
public class WaitingForAckProtocolState extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(WaitingForAckProtocolState.class);

  private final SwarmAddress pingTarget;
  private final Duration jitteredMessageTimeout;

  WaitingForAckProtocolState(
    SwarmConfig swarmConfig,
    SwarmAddress pingTarget,
    long protocolPeriodId,
    long incarnation,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry
  ) {
    super(swarmConfig, protocolPeriodId, incarnation, stopwatch, memberRegistry);
    this.pingTarget = pingTarget;
    this.jitteredMessageTimeout = Jitter.apply(
      swarmConfig.getMessageTimeout(),
      swarmConfig.getMessageTimeoutJitter()
    );
  }

  @Override
  public Optional<Transition> applyTick() {
    if (stopwatch.elapsed().toNanos() < jitteredMessageTimeout.toNanos()) {
      return Optional.empty();
    }

    Set<SwarmAddress> failureSubGroup = registry.getFailureSubGroup(
      swarmConfig.getFailureSubGroup(),
      pingTarget
    );

    List<StateMachineMessage> responses = failureSubGroup
      .stream()
      .map(swarmNode ->
        new PingRequest(
            swarmConfig.getLocalAddress(),
          swarmNode,
          Optional.of(pingTarget),
          protocolPeriodId
        )
      )
      .collect(ImmutableList.toImmutableList());

    SwarmProtocolState nextState = new WaitingForPingProxyProtocolState(
      swarmConfig,
      protocolPeriodId,
      incarnation,
      stopwatch,
      registry,
      pingTarget,
      failureSubGroup
    );

    return Optional.of(
      Transition
        .builder()
        .setNextSwarmProtocolState(nextState)
        .addAllResponsesToSend(responses)
        .build()
    );
  }

  @Override
  public Optional<Transition> applyPingAck(PingAck pingAck) {
    if (!pingAck.source().equals(pingTarget)) {
      LOG.debug("Received ACK source {}, expecting ACK source {}", pingAck.source(), pingTarget);
      return Optional.empty();
    }

    WaitingForNextProtocolPeriodProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
      swarmConfig,
      ThreadLocalRandom.current().nextLong(),
      incarnation,
      stopwatch,
      registry
    );

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(nextState)
      .build();

    return Optional.of(transition);
  }
}
