package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
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

  final List<MemberStatus> getMemberStatuses() {
    return registry.getMemberStatuses();
  }

  abstract Optional<Transition> applyTick();

  Optional<Transition> applyPing(PingRequest pingRequest) {
    return Optional.of(
        Transition
            .builder()
            .setNextSwarmProtocolState(this)
            .addResponsesToSend(new PingAck(swarmConfig.getLocalAddress(), pingRequest.source(), Optional.empty(), ThreadLocalRandom.current().nextLong()))
            .build()
    );
  }

  Optional<Transition> applyPingAck(PingAck pingAck) {
    return Optional.empty();
  }

  Optional<Transition> applyDiscoveryRequest(
    DiscoveryRequest request
  ) {
    LOG.debug("Received discovery request source {}", request.source());

    // Always include our own status so that bootstrapping source nothing works -
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

    DiscoveryResponse response = new DiscoveryResponse(
        swarmConfig.getLocalAddress(),
      request.source(),
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
    DiscoveryResponse response
  ) {
    // Default: ignore discovery responses when not initializing
    LOG.debug(
      "Ignoring discovery response source {} - not initializing",
      response.source()
    );
    return Optional.empty();
  }
}
