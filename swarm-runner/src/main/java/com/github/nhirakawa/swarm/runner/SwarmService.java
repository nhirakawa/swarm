package com.github.nhirakawa.swarm.runner;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.transport.server.SwarmNettyServer;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ServiceManager;

public class SwarmService {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmService.class);

  private final SwarmTimer swarmTimer;
  private final SwarmNettyServer swarmNettyServer;
  private final SwarmDisseminator swarmDisseminator;
  private final SwarmStateMachine swarmStateMachine;

  @Inject
  SwarmService(
    SwarmTimer swarmTimer,
    SwarmNettyServer swarmNettyServer,
    SwarmDisseminator swarmDisseminator,
    SwarmStateMachine swarmStateMachine
  ) {
    this.swarmTimer = swarmTimer;
    this.swarmNettyServer = swarmNettyServer;
    this.swarmDisseminator = swarmDisseminator;
    this.swarmStateMachine = swarmStateMachine;
  }

  public void run() {
    ServiceManager serviceManager = new ServiceManager(
      ImmutableList.of(
        swarmTimer,
        swarmNettyServer,
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
