package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.base.Preconditions;
import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmTimer implements Initializable {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmTimer.class);

  private final Object lock = new Object();

  private final ScheduledExecutorService scheduledExecutorService;
  private final SwarmMessageApplier swarmMessageApplier;
  private final SwarmConfig swarmConfig;
  private final Clock clock;

  private Optional<ScheduledFuture<?>> scheduledFuture = Optional.empty();

  @Inject
  public SwarmTimer(
    ScheduledExecutorService scheduledExecutorService,
    SwarmMessageApplier swarmMessageApplier,
    SwarmConfig swarmConfig,
    Clock clock
  ) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.swarmMessageApplier = swarmMessageApplier;
    this.swarmConfig = swarmConfig;
    this.clock = clock;
  }

  public void start() {
    synchronized (lock) {
      Preconditions.checkState(
        !scheduledFuture.isPresent(),
        "Timer has already been started"
      );

      scheduledFuture =
        Optional.of(
          scheduledExecutorService.scheduleAtFixedRate(
            this::doTimeout,
            0L,
            swarmConfig.getProtocolTick().toMillis(),
            TimeUnit.MILLISECONDS
          )
        );
    }
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

  @Override
  public void initialize() {
    start();
  }
}
