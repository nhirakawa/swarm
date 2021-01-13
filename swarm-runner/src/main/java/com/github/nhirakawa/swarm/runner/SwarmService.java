package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.protocol.util.EventBusLogger;
import com.google.common.util.concurrent.Service;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmService {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmService.class);

  private final SwarmTimer swarmTimer;
  private final Service swarmServer;
  private final SwarmDisseminator swarmDisseminator;
  private final SwarmStateMachine swarmStateMachine;
  private final EventBusLogger eventBusLogger;

  @Inject
  SwarmService(
    SwarmTimer swarmTimer,
    @Named("swarm-server") Service swarmServer,
    SwarmDisseminator swarmDisseminator,
    SwarmStateMachine swarmStateMachine,
    EventBusLogger eventBusLogger
  ) {
    this.swarmTimer = swarmTimer;
    this.swarmServer = swarmServer;
    this.swarmDisseminator = swarmDisseminator;
    this.swarmStateMachine = swarmStateMachine;
    this.eventBusLogger = eventBusLogger;
  }

  public void run() {
    eventBusLogger.startAsync().awaitRunning();
    swarmServer.startAsync().awaitRunning();
    LOG.debug("SwarmServer - {}", swarmServer.state());
    swarmDisseminator.startAsync().awaitRunning();
    swarmStateMachine.startAsync().awaitRunning();
    swarmTimer.startAsync().awaitRunning();
  }
}
