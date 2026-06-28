package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import java.time.Duration;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class InitializingProtocolState extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    InitializingProtocolState.class
  );

	private final int attemptNumber;

  InitializingProtocolState(ProtocolStateContext context) {
    this(context, 0);
  }

  private InitializingProtocolState(ProtocolStateContext context, int attemptNumber) {
    super(context);
		this.attemptNumber = attemptNumber;
  }

  @Override
  public Optional<Transition> applyTick() {
    if (context().elapsed().toNanos() < Duration.ofSeconds(5).toNanos()) {
      return Optional.empty(); // Not time to retry yet
    }

    LOG.debug("Tick fired - registry size is {}", context().memberRegistry().size());

    if (context().memberRegistry().size() > 0) {
      // Transition to normal operation
      SwarmProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
          context().next()
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
      context().next(),
      attemptNumber + 1
    );

    DiscoveryRequest discoveryRequest = new DiscoveryRequest(
        context().swarmConfig().getLocalAddress(),
        context().swarmConfig().getMulticastAddress()
    );

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
      context().memberRegistry().put(memberStatus.address(), memberStatus);
    }

    LOG.info("Registry size after merge: {}", context().memberRegistry().size());

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
