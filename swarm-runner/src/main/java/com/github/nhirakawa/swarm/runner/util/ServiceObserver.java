package com.github.nhirakawa.swarm.runner.util;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import io.netty.handler.logging.LogLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceObserver extends AbstractScheduledService {

  private static final Logger LOG = LoggerFactory.getLogger(
    ServiceObserver.class
  );

  private static final Set<State> TERMINAL_STATES = EnumSet.of(
    State.TERMINATED,
    State.FAILED
  );

  private final List<NamedService> services;

  public ServiceObserver(List<NamedService> services) {
    this.services = new ArrayList<>(services);
  }

  @Override
  protected void runOneIteration() throws Exception {
    for (NamedService service : services) {
      if (TERMINAL_STATES.contains(service.state())) {
        LOG.info("Service {} has state {}", service.getName(), service.state());
        stopAsync();
      }
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
      Duration.ofSeconds(1),
      Duration.ofMillis(100)
    );
  }
}
