package com.github.nhirakawa.swarm.protocol.dagger;

import com.github.nhirakawa.swarm.protocol.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageApplier;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import dagger.Module;
import dagger.multibindings.ElementsIntoSet;
import dagger.Provides;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Set;
import java.util.UUID;
import javax.inject.Singleton;

@Module
public class SwarmProtocolModule {
  private final SwarmConfig swarmConfig;

  public SwarmProtocolModule(SwarmConfig swarmConfig) {
    this.swarmConfig = swarmConfig;
  }

  @Provides
  @Singleton
  SwarmConfig provideSwarmConfig() {
    return swarmConfig;
  }

  @Provides
  @Singleton
  static Clock provideClock() {
    return Clock.systemUTC();
  }

  @Provides
  @Singleton
  static InjectableRandom provideInjectableRandom() {
    return new InjectableThreadLocalRandom();
  }

  @Provides
  @Singleton
  static ScheduledExecutorService provideScheduledExecutorService(
    SwarmConfig swarmConfig
  ) {
    return Executors.newScheduledThreadPool(
      1,
      SwarmThreadFactoryFactory.forNode(
        "swarm-scheduled",
        swarmConfig.getLocalNode()
      )
    );
  }

  @Provides
  @Singleton
  static EventBus provideEventBus() {
    return new EventBus("swarm");
  }

  @Provides
  @ElementsIntoSet
  static Set<Initializable> provideEventBusRegisters(
    SwarmTimer swarmTimer,
    SwarmMessageApplier swarmMessageApplier,
    SwarmDisseminator swarmDisseminator
  ) {
    return ImmutableSet.of(swarmTimer, swarmMessageApplier, swarmDisseminator);
  }

  @Provides
  @Singleton
  static SwarmState provideInitialSwarmState(Clock clock) {
    Instant now = clock.instant();
    return SwarmState
      .builder()
      .setLastProtocolPeriodStarted(now)
      .setTimestamp(now)
      .setLastProtocolPeriodId(UUID.randomUUID().toString())
      .build();
  }

  @Provides
  @Singleton
  static SwarmStateBuffer provideSwarmStateBuffer(
    SwarmState swarmState,
    SwarmConfig swarmConfig
  ) {
    return new SwarmStateBuffer(
      swarmState,
      swarmConfig.getSwarmStateBufferSize()
    );
  }
}
