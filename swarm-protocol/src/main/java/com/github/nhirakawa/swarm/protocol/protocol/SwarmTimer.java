package com.github.nhirakawa.swarm.protocol.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.typesafe.config.Config;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmTimer {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmTimer.class);

  private final ScheduledExecutorService scheduledExecutorService;
  private final SwarmMessageApplier swarmMessageApplier;
  private final Config config;

  private Optional<ScheduledFuture<?>> scheduledFuture = Optional.empty();

  @Inject
  public SwarmTimer(
    ScheduledExecutorService scheduledExecutorService,
    SwarmMessageApplier swarmMessageApplier,
    Config config
  ) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.swarmMessageApplier = swarmMessageApplier;
    this.config = config;
  }

  public synchronized void start() {
    Preconditions.checkState(
      !scheduledFuture.isPresent(),
      "Timer has already been started"
    );

    scheduledFuture =
      Optional.of(
        scheduledExecutorService.scheduleAtFixedRate(
          this::doTimeout,
          0L,
          config
            .getDuration(ConfigPath.SWARM_PROTOCOL_TICK.getConfigPath())
            .toMillis(),
          TimeUnit.MILLISECONDS
        )
      );
  }

  private void doTimeout() {
    LOG.trace("timeout");

    try {
      swarmMessageApplier.apply(
        SwarmTimeoutMessage.builder().setTImestamp(Instant.now()).build()
      );
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
