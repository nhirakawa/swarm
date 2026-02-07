package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequestResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineResponse;
import com.github.nhirakawa.swarm.protocol.util.JitterUtil;
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
    this.jitteredMessageTimeout = JitterUtil.applyJitter(
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

    List<StateMachineResponse> responses = failureSubGroup
      .stream()
      .map(swarmNode ->
        new PingRequestResponse(
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
  public Optional<Transition> applyPingAck(InboundPingAck pingAck) {
    if (!pingAck.from().equals(pingTarget)) {
      LOG.debug("Received ACK from {}, expecting ACK from {}", pingAck.from(), pingTarget);
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
