package com.github.nhirakawa.swarm.protocol.dagger;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Singleton;

import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.ConfigValidator;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageApplier;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.typesafe.config.Config;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

@Module
public class SwarmProtocolModule {
  private final Config config;
  private final SwarmNode localSwarmNode;
  private final Set<SwarmNode> clusterNodes;

  public SwarmProtocolModule(
    Config config,
    SwarmNode localSwarmNode,
    Set<SwarmNode> clusterNodes
  ) {
    this.config = ConfigValidator.validate(config);
    this.localSwarmNode = localSwarmNode;
    this.clusterNodes =
      Sets.difference(clusterNodes, Collections.singleton(localSwarmNode));
  }

  @Provides
  @Singleton
  Config provideConfig() {
    return config;
  }

  @Provides
  @Singleton
  SwarmNode provideLocalSwarmNode() {
    return localSwarmNode;
  }

  @Provides
  @Singleton
  Set<SwarmNode> provideSwarmClusterNodes() {
    return clusterNodes;
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
    SwarmNode swarmNode
  ) {
    return Executors.newScheduledThreadPool(
      1,
      SwarmThreadFactoryFactory.forNode("swarm-scheduled", swarmNode)
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
      .build();
  }

  @Provides
  @Singleton
  static SwarmStateBuffer provideSwarmStateBuffer(
    SwarmState swarmState,
    Config config
  ) {
    return new SwarmStateBuffer(
      swarmState,
      config.getInt(ConfigPath.SWARM_STATE_BUFFER_SIZE.getConfigPath())
    );
  }
}
