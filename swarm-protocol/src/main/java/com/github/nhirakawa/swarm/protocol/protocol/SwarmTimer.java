package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractScheduledService;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmTimer extends AbstractScheduledService {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmTimer.class);

  private final EventBus eventBus;
  private final SwarmConfig swarmConfig;
  private final Clock clock;
  private final ScheduledExecutorService scheduledExecutorService;

  @Inject
  public SwarmTimer(
    EventBus eventBus,
    SwarmConfig swarmConfig,
    Clock clock,
    ScheduledExecutorService scheduledExecutorService
  ) {
    this.eventBus = eventBus;
    this.swarmConfig = swarmConfig;
    this.clock = clock;
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  protected void runOneIteration() throws Exception {
    //    LOG.trace("Protocol tick");
    eventBus.post(
      SwarmTimeoutMessage.builder().setTimestamp(clock.instant()).build()
    );
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
      Duration.ZERO,
      swarmConfig.getProtocolTick()
    );
  }

  @Override
  protected ScheduledExecutorService executor() {
    return scheduledExecutorService;
  }

  @Override
  protected String serviceName() {
    return String.format(
      "swarm-timer-%s-%s",
      swarmConfig.getLocalNode().getHost(),
      swarmConfig.getLocalNode().getPort()
    );
  }
}
