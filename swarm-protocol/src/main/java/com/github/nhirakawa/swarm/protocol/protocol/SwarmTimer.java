package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.util.concurrent.AbstractScheduledService;
import java.time.Clock;
import java.time.Duration;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmTimer extends AbstractScheduledService {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmTimer.class);

  private final SwarmMessageApplier swarmMessageApplier;
  private final SwarmConfig swarmConfig;
  private final Clock clock;

  @Inject
  public SwarmTimer(
    SwarmMessageApplier swarmMessageApplier,
    SwarmConfig swarmConfig,
    Clock clock
  ) {
    this.swarmMessageApplier = swarmMessageApplier;
    this.swarmConfig = swarmConfig;
    this.clock = clock;
  }

  private void doTimeout() {
    LOG.trace("timeout");

    try {
      swarmMessageApplier.apply(
        SwarmTimeoutMessage.builder().setTimestamp(clock.instant()).build()
      );
    } catch (Exception e) {
      LOG.error("Uncaught exception in timer", e);
    }
  }

  //  @Override
  //  public void initialize() {
  //    start();
  //  }
  @Override
  protected void runOneIteration() throws Exception {
    try {
      swarmMessageApplier.apply(
        SwarmTimeoutMessage.builder().setTimestamp(clock.instant()).build()
      );
    } catch (Exception e) {
      LOG.error("Uncaught exception in timer", e);
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
      Duration.ZERO,
      swarmConfig.getProtocolTick()
    );
  }
}
