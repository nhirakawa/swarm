package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.protocol.util.DeadEventLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
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
  private final DeadEventLogger deadEventLogger;

  @Inject
  SwarmService(
    SwarmTimer swarmTimer,
    @Named("swarm-server") Service swarmServer,
    SwarmDisseminator swarmDisseminator,
    SwarmStateMachine swarmStateMachine,
    DeadEventLogger deadEventLogger
  ) {
    this.swarmTimer = swarmTimer;
    this.swarmServer = swarmServer;
    this.swarmDisseminator = swarmDisseminator;
    this.swarmStateMachine = swarmStateMachine;
    this.deadEventLogger = deadEventLogger;
  }

  public void run() {
    ServiceManager serviceManager = new ServiceManager(
      ImmutableList.of(
        swarmTimer,
        swarmServer,
        swarmDisseminator,
        swarmStateMachine
      )
    );

    serviceManager.startAsync();
    try {
      serviceManager.awaitHealthy(Duration.ofSeconds(10));
    } catch (TimeoutException e) {
      LOG.error("Could not start SwarmService", e);
      throw new RuntimeException(e);
    }
  }
}
