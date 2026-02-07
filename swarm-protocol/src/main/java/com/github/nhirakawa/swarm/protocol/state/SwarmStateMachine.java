package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundDiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundDiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineResponse;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// todo(nhirakawa) - document this
@ThreadSafe
public class SwarmStateMachine extends AbstractExecutionThreadService {

  private static final Logger LOG = LogManager.getLogger(
    SwarmStateMachine.class
  );

  private final SwarmConfig swarmConfig;
  private final SwarmMessageReceiver swarmMessageReceiver;
  private final SwarmMessageSender swarmMessageSender;

  private SwarmProtocolState swarmProtocolState;

  public SwarmStateMachine(
    SwarmConfig swarmConfig,
    SwarmMessageReceiver swarmMessageReceiver,
    SwarmMessageSender swarmMessageSender
  ) {
    this.swarmConfig = swarmConfig;
    this.swarmMessageReceiver = swarmMessageReceiver;
    this.swarmMessageSender = swarmMessageSender;
  }

  @Override
  protected void startUp() {
    LOG.info("Starting state machine");

    MemberRegistry memberRegistry = new MemberRegistry(Set.of());

    this.swarmProtocolState =
      SwarmProtocolState.initial(
        swarmConfig,
        ThreadLocalRandom.current().nextLong(),
        Stopwatch.createStarted(),
        memberRegistry
      );
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      Optional<StateMachineMessage> maybeStateMachineMessage = swarmMessageReceiver.receive(
          swarmConfig.getProtocolTick()
      );

      Optional<Transition> maybeTickTransition = applyTick();
      if (maybeTickTransition.isPresent()) {
        handleTransition(maybeTickTransition.get());
      }

      var maybeTransition = maybeStateMachineMessage.flatMap(stateMachineMessage -> switch (stateMachineMessage) {
				case InboundPingAck inboundPingAck -> applyPingAck(inboundPingAck);
				case InboundPingRequest inboundPingRequest -> applyPingRequest(inboundPingRequest);
				case InboundDiscoveryRequest inboundDiscoveryRequest -> applyDiscoveryRequest(inboundDiscoveryRequest);
				case InboundDiscoveryResponse inboundDiscoveryResponse -> applyDiscoveryResponse(inboundDiscoveryResponse);
			});

      if (maybeTransition.isPresent()) {
        handleTransition(maybeTransition.get());
      }
    }
  }

  private void handleTransition(Transition transition) {
    if (!swarmProtocolState.getClass().getSimpleName().equals(transition.getNextSwarmProtocolState().getClass().getSimpleName())) {
      LOG.debug(
          "Transitioning from {} to {}",
          swarmProtocolState.getClass().getSimpleName(),
          transition.getNextSwarmProtocolState().getClass().getSimpleName()
      );
    }
    swarmProtocolState = transition.getNextSwarmProtocolState();

    for (StateMachineResponse response : transition.getResponsesToSend()) {
      swarmMessageSender.send(response);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Shutting down state machine");
  }

  @Override
  protected String serviceName() {
    return "%s-%s-state-machine".formatted(swarmConfig.getLocalAddress().address(), swarmConfig.getLocalAddress().port());
  }

  public String getName() {
    return serviceName();
  }

  private Optional<Transition> applyTick() {
    return swarmProtocolState.applyTick();
  }

  private Optional<Transition> applyPingAck(InboundPingAck pingAck) {
    return swarmProtocolState.applyPingAck(pingAck);
  }

  private Optional<Transition> applyPingRequest(
    InboundPingRequest pingRequest
  ) {
    return swarmProtocolState.applyPing(pingRequest);
  }

  private Optional<Transition> applyDiscoveryRequest(
    InboundDiscoveryRequest discoveryRequest
  ) {
    return swarmProtocolState.applyDiscoveryRequest(discoveryRequest);
  }

  private Optional<Transition> applyDiscoveryResponse(
    InboundDiscoveryResponse discoveryResponse
  ) {
    return swarmProtocolState.applyDiscoveryResponse(discoveryResponse);
  }
}
