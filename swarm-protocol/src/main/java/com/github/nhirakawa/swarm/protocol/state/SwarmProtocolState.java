package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAckResponse;
import com.google.common.base.Stopwatch;
import java.util.Optional;

public abstract class SwarmProtocolState {

  protected final SwarmConfig swarmConfig;
  protected final String protocolPeriodId;
  final Stopwatch stopwatch;
  final MemberRegistry registry;

  SwarmProtocolState(
    SwarmConfig swarmConfig,
    String protocolPeriodId,
    Stopwatch stopwatch,
    MemberRegistry registry
  ) {
    this.swarmConfig = swarmConfig;
    this.protocolPeriodId = protocolPeriodId;
    this.stopwatch = stopwatch.reset().start();
    this.registry = registry;
  }

  static SwarmProtocolState initial(
    SwarmConfig swarmConfig,
    String protocolPeriodId,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry
  ) {
    return new WaitingForNextProtocolPeriodProtocolState(
      swarmConfig,
      protocolPeriodId,
      stopwatch,
      memberRegistry
    );
  }

  String getProtocolPeriodId() {
    return protocolPeriodId;
  }

  abstract Optional<Transition> applyTick();

  Optional<Transition> applyPing(InboundPingRequest pingRequest) {
    return Optional.of(
        Transition
            .builder()
            .setNextSwarmProtocolState(this)
            .addResponsesToSend(new PingAckResponse(pingRequest.from(), Optional.empty(), protocolPeriodId))
            .build()
    );
  }

  Optional<Transition> applyPingAck(InboundPingAck pingAck) {
    return Optional.empty();
  }
}
