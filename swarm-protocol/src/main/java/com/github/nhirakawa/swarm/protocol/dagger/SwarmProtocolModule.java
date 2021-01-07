package com.github.nhirakawa.swarm.protocol.dagger;

import com.github.nhirakawa.swarm.protocol.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import com.google.common.eventbus.EventBus;
import dagger.Module;
import dagger.Provides;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
}
