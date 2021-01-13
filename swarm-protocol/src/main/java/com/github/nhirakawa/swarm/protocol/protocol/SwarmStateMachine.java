package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingRequestMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.state.SwarmProtocolState;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ThreadSafe
public class SwarmStateMachine extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmStateMachine.class
  );
  private final Object lock = new Object();

  private final SwarmConfig swarmConfig;
  private final EventBus eventBus;
  private final SwarmFailureInjector swarmFailureInjector;

  private SwarmProtocolState swarmProtocolState;

  @Inject
  SwarmStateMachine(
    SwarmConfig swarmConfig,
    Clock clock,
    EventBus eventBus,
    SwarmFailureInjector swarmFailureInjector
  ) {
    this.swarmConfig = swarmConfig;
    this.eventBus = eventBus;
    this.swarmFailureInjector = swarmFailureInjector;

    this.swarmProtocolState =
      SwarmProtocolState.initial(
        clock.instant(),
        swarmConfig,
        UUID.randomUUID().toString()
      );
  }

  @Subscribe
  public void applyTimeout(SwarmTimeoutMessage timeoutMessage) {
    State state = state();
    if (state != State.RUNNING) {
      LOG.debug("Current state is {}", state);
      return;
    }

    synchronized (lock) {
      Optional<Transition> transition = swarmProtocolState.applyTick(
        timeoutMessage
      );

      transition.ifPresent(this::applyStateAndSendMessages);
    }
  }

  @Subscribe
  public void applyAck(PingAckMessage pingAckMessage) {
    State state = state();
    if (state != State.RUNNING) {
      LOG.debug("Current state is {}", state);
      return;
    }

    if (pingAckMessage.getFrom().equals(swarmConfig.getLocalNode())) {
      return;
    }

    synchronized (lock) {
      Optional<Transition> transition = swarmProtocolState.applyPingAck(
        pingAckMessage
      );

      transition.ifPresent(this::applyStateAndSendMessages);
    }
  }

  @Subscribe
  public void applyPingRequest(PingRequestMessage pingRequestMessage) {
    State state = state();
    if (state != State.RUNNING) {
      LOG.debug("Current state is {}", state);
      return;
    }

    if (swarmFailureInjector.shouldInjectFailure()) {
      LOG.debug("Injecting failure - dropping {}", pingRequestMessage);
      return;
    }

    PingAckMessage pingAckMessage = PingAckMessage
      .builder()
      .setUniqueMessageId(UUID.randomUUID().toString())
      .setFrom(swarmConfig.getLocalNode())
      .setTo(pingRequestMessage.getFrom())
      .setProtocolPeriodId(swarmProtocolState.getProtocolPeriodId())
      .build();

    eventBus.post(pingAckMessage);
  }

  private void applyStateAndSendMessages(Transition transition) {
    LOG.trace(
      "Transitioning from {} to {}",
      swarmProtocolState,
      transition.getNextSwarmProtocolState()
    );

    swarmProtocolState = transition.getNextSwarmProtocolState();

    for (BaseSwarmMessage message : transition.getMessagesToSend()) {
      eventBus.post(message);
    }
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting state machine");
    eventBus.register(this);
  }

  @Override
  protected void shutDown() throws Exception {
    eventBus.unregister(this);
  }
}
