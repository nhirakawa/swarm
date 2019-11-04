package com.github.nhirakawa.swarm.protocol.protocol;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.base.Preconditions;
import com.typesafe.config.Config;

public class SwarmTimer {

  private final ScheduledExecutorService scheduledExecutorService;
  private final SwarmProtocol swarmProtocol;
  private final Config config;

  private Optional<ScheduledFuture<?>> scheduledFuture = Optional.empty();

  @Inject
  public SwarmTimer(ScheduledExecutorService scheduledExecutorService,
                    SwarmProtocol swarmProtocol,
                    Config config) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.swarmProtocol = swarmProtocol;
    this.config = config;
  }

  public synchronized void start() {
    Preconditions.checkState(!scheduledFuture.isPresent(), "Timer has already been started");

    scheduledFuture = Optional.of(
        scheduledExecutorService.scheduleAtFixedRate(
            this::doTimeout,
            0L,
            config.getDuration(ConfigPath.SWARM_PROTOCOL_TICK.getConfigPath()).toMillis(),
            TimeUnit.MILLISECONDS
        )
    );
  }

  private void doTimeout() {
    try {
      swarmProtocol.handle(
          SwarmTimeoutMessage.builder()
              .setTImestamp(Instant.now())
              .build()
      );
    } catch (JsonProcessingException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
