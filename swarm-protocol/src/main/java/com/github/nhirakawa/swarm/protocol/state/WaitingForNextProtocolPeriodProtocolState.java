package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.util.Jitter;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// todo(nhirakawa) document this
public class WaitingForNextProtocolPeriodProtocolState
  extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    WaitingForNextProtocolPeriodProtocolState.class
  );

  private final Duration jitteredProtocolPeriod;

  WaitingForNextProtocolPeriodProtocolState(
    SwarmConfig swarmConfig,
    long protocolPeriodId,
    long incarnation,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry
  ) {
    super(swarmConfig, protocolPeriodId, incarnation, stopwatch, memberRegistry);
    this.jitteredProtocolPeriod = Jitter.apply(
      swarmConfig.getProtocolPeriod(),
      swarmConfig.getProtocolPeriodJitter()
    );
  }

  @Override
  public Optional<Transition> applyTick() {
    if (stopwatch.elapsed().toNanos() < jitteredProtocolPeriod.toNanos()) {
      return Optional.empty();
    }

    LOG.debug("Current protocol period has ended");

    SwarmAddress pingTarget = registry.getPingTarget();

    SwarmProtocolState newState = new WaitingForAckProtocolState(
      swarmConfig,
      pingTarget,
      ThreadLocalRandom.current().nextLong(),
      incarnation,
      stopwatch,
      registry
    );

    StateMachineMessage pingRequest = new PingRequest(
        swarmConfig.getLocalAddress(),
      pingTarget,
      Optional.empty(),
      protocolPeriodId
    );

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(newState)
      .addResponsesToSend(pingRequest)
      .build();

    return Optional.of(transition);
  }

  @Override
  public Optional<Transition> applyPingAck(PingAck pingAck) {
    LOG.debug("Ignoring {}", pingAck);
    return Optional.empty();
  }
}
