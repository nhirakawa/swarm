package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InitializingProtocolState extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    InitializingProtocolState.class
  );

	private final int attemptNumber;

  InitializingProtocolState(
    SwarmConfig swarmConfig,
    long protocolPeriodId,
    long incarnation,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry
  ) {
    this(
      swarmConfig,
      protocolPeriodId,
      incarnation,
      stopwatch,
      memberRegistry,
      0
    );
  }

  private InitializingProtocolState(
    SwarmConfig swarmConfig,
    long protocolPeriodId,
    long incarnation,
    Stopwatch stopwatch,
    MemberRegistry memberRegistry,
		int attemptNumber
  ) {
    super(swarmConfig, protocolPeriodId, incarnation, stopwatch, memberRegistry);
		this.attemptNumber = attemptNumber;
  }

  @Override
  public Optional<Transition> applyTick() {
    if (stopwatch.elapsed().toNanos() < Duration.ofSeconds(5).toNanos()) {
      return Optional.empty(); // Not time to retry yet
    }

    if (registry.size() > 0) {
      // Transition to normal operation
      SwarmProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
          swarmConfig,
          ThreadLocalRandom.current().nextLong(),
          incarnation,
          Stopwatch.createStarted(),
          registry
      );

      return Optional.of(
          Transition.builder().setNextSwarmProtocolState(nextState).build()
      );
    }

    LOG.debug(
      "Broadcasting discovery request (attempt {})",
      attemptNumber + 1
    );

    // Create a new state for next retry (if needed)
    InitializingProtocolState nextRetryState = new InitializingProtocolState(
      swarmConfig,
      protocolPeriodId,
      incarnation,
      Stopwatch.createStarted(), // Reset stopwatch for next retry
      registry,
      attemptNumber + 1
    );

    DiscoveryRequest discoveryRequest = new DiscoveryRequest(swarmConfig.getLocalAddress());

    return Optional.of(
      Transition.builder()
        .setNextSwarmProtocolState(nextRetryState)
        .addResponsesToSend(discoveryRequest)
        .build()
    );
  }

  @Override
  Optional<Transition> applyDiscoveryResponse(DiscoveryResponse response) {
    LOG.info(
      "Received discovery response source {} with {} members after {} attempts",
      response.source(),
      response.memberList().size(),
      attemptNumber + 1
    );

    // Merge received member list into registry
    for (MemberStatus memberStatus : response.memberList()) {
      registry.put(memberStatus.address(), memberStatus);
    }

    return Optional.empty();
  }

  @Override
  Optional<Transition> applyDiscoveryRequest(DiscoveryRequest request) {
    // During initialization, respond to discovery requests source other nodes
    LOG.debug(
      "Received discovery request source {} during initialization",
      request.source()
    );

    // Use parent implementation to respond with member list
    return super.applyDiscoveryRequest(request);
  }
}
