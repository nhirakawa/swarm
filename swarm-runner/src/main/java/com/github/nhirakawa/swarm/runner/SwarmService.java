package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.protocol.util.EventBusLogger;
import com.github.nhirakawa.swarm.runner.util.NamedService;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwarmService

extends AbstractScheduledService
  implements NamedService {

  private static final Logger LOG = LogManager.getLogger(SwarmService.class);

  private final SwarmTimer swarmTimer;
  private final Service swarmServer;
  private final SwarmDisseminator swarmDisseminator;
  private final SwarmStateMachine swarmStateMachine;
  private final EventBusLogger eventBusLogger;
  private final SwarmConfig swarmConfig;

  @Inject
  SwarmService(
    SwarmTimer swarmTimer,
    @Named("swarm-server") Service swarmServer,
    SwarmDisseminator swarmDisseminator,
    SwarmStateMachine swarmStateMachine,
    EventBusLogger eventBusLogger,
    SwarmConfig swarmConfig
  ) {
    this.swarmTimer = swarmTimer;
    this.swarmServer = swarmServer;
    this.swarmDisseminator = swarmDisseminator;
    this.swarmStateMachine = swarmStateMachine;
    this.eventBusLogger = eventBusLogger;
    this.swarmConfig = swarmConfig;
  }

  @Override
  protected void startUp() throws Exception {
    eventBusLogger.startAsync().awaitRunning(Duration.ofSeconds(1));
    swarmServer.startAsync().awaitRunning(Duration.ofSeconds(1));
    swarmDisseminator.startAsync().awaitRunning(Duration.ofSeconds(1));
    swarmStateMachine.startAsync().awaitRunning(Duration.ofSeconds(1));
    swarmTimer.startAsync().awaitRunning(Duration.ofSeconds(1));
  }

  @Override
  protected void shutDown() throws Exception {
    swarmTimer.stopAsync();
    swarmStateMachine.stopAsync();
    swarmDisseminator.stopAsync();
    swarmServer.stopAsync();
    eventBusLogger.stopAsync();
  }

  @Override
  protected void runOneIteration() throws Exception {
    if (!isRunning()) {
      return;
    }

    if (
      !eventBusLogger.isRunning() ||
      !swarmServer.isRunning() ||
      !swarmDisseminator.isRunning() ||
      !swarmStateMachine.isRunning() ||
      !swarmTimer.isRunning()
    ) {
      stopAsync();
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
      Duration.ofSeconds(1),
      Duration.ofMillis(100)
    );
  }

  @Override
  protected String serviceName() {
    return String.format(
      "swarm-service-%s-%s",
      swarmConfig.getLocalNode().getHost(),
      swarmConfig.getLocalNode().getPort()
    );
  }

  @Override
  public String getName() {
    return serviceName();
  }
}
