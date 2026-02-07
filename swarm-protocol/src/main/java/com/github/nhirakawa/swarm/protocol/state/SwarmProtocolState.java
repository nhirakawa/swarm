package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponseResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundDiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundDiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAckResponse;
import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    SwarmProtocolState.class
  );

  protected final SwarmConfig swarmConfig;
  protected final long protocolPeriodId;
  protected final long incarnation;
  final Stopwatch stopwatch;
  final MemberRegistry registry;

  SwarmProtocolState(
    SwarmConfig swarmConfig,
    long protocolPeriodId,
    long incarnation,
    Stopwatch stopwatch,
    MemberRegistry registry
  ) {
    this.swarmConfig = swarmConfig;
    this.protocolPeriodId = protocolPeriodId;
    this.incarnation = incarnation;
    this.stopwatch = stopwatch.reset().start();
    this.registry = registry;
  }

  static SwarmProtocolState initial(
    SwarmConfig swarmConfig,
    long protocolPeriodId,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry
  ) {
    if (swarmConfig.isDiscoveryEnabled()) {
      return new InitializingProtocolState(
        swarmConfig,
        protocolPeriodId,
        0L,
        stopwatch,
        memberRegistry
      );
    } else {
      return new WaitingForNextProtocolPeriodProtocolState(
        swarmConfig,
        protocolPeriodId,
        0L,
        stopwatch,
        memberRegistry
      );
    }
  }

  abstract Optional<Transition> applyTick();

  Optional<Transition> applyPing(InboundPingRequest pingRequest) {
    return Optional.of(
        Transition
            .builder()
            .setNextSwarmProtocolState(this)
            .addResponsesToSend(new PingAckResponse(pingRequest.from(), Optional.empty(), ThreadLocalRandom.current().nextLong()))
            .build()
    );
  }

  Optional<Transition> applyPingAck(InboundPingAck pingAck) {
    return Optional.empty();
  }

  Optional<Transition> applyDiscoveryRequest(
    InboundDiscoveryRequest request
  ) {
    LOG.debug("Received discovery request from {}", request.from());

    // Always include our own status so that bootstrapping from nothing works -
    // even if the registry is empty, the requester learns about us.
    MemberStatus self = MemberStatus.alive(swarmConfig.getLocalAddress(), incarnation);
    List<MemberStatus> gossip = registry.getGossipPayload(10);

    List<MemberStatus> memberList = new ArrayList<>(gossip.size() + 1);
    memberList.add(self);
    for (MemberStatus m : gossip) {
      if (!m.address().equals(swarmConfig.getLocalAddress())) {
        memberList.add(m);
      }
    }

    DiscoveryResponseResponse response = new DiscoveryResponseResponse(
      request.from(),
      memberList
    );

    return Optional.of(
      Transition.builder()
        .setNextSwarmProtocolState(this)
        .addResponsesToSend(response)
        .build()
    );
  }

  Optional<Transition> applyDiscoveryResponse(
    InboundDiscoveryResponse response
  ) {
    // Default: ignore discovery responses when not initializing
    LOG.debug(
      "Ignoring discovery response from {} - not initializing",
      response.from()
    );
    return Optional.empty();
  }
}
