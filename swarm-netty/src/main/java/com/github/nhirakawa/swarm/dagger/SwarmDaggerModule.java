package com.github.nhirakawa.swarm.dagger;

import com.github.nhirakawa.swarm.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmDisseminator;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageApplier;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.typesafe.config.Config;
import dagger.Module;
import dagger.multibindings.ElementsIntoSet;
import dagger.Provides;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Set;
import javax.inject.Singleton;

@Module(includes = SwarmProtocolModule.class)
public class SwarmDaggerModule {
  private final Config config;
  private final SwarmNode localSwarmNode;
  private final Set<SwarmNode> clusterNodes;

  public SwarmDaggerModule(
    Config config,
    SwarmNode localSwarmNode,
    Set<SwarmNode> clusterNodes
  ) {
    this.config = config;
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
  @IncomingQueue
  static BlockingQueue<BaseSwarmMessage> provideIncomingQueue() {
    return new ArrayBlockingQueue<>(100);
  }

  @Provides
  @Singleton
  @OutgoingQueue
  static BlockingQueue<BaseSwarmMessage> provideOutgoingQueue() {
    return new ArrayBlockingQueue<>(100);
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
  ScheduledExecutorService provideScheduledExecutorService(
    SwarmNode swarmNode
  ) {
    return Executors.newScheduledThreadPool(
      4,
      SwarmThreadFactoryFactory.forNode("swarm-scheduled", swarmNode)
    );
  }

  @Provides
  @Singleton
  EventBus provideEventBus() {
    return new EventBus("swarm");
  }

  @Provides
  @ElementsIntoSet
  Set<Initializable> provideEventBusRegisters(
    SwarmTimer swarmTimer,
    SwarmMessageApplier swarmMessageApplier,
    SwarmDisseminator swarmDisseminator
  ) {
    return ImmutableSet.of(swarmTimer, swarmMessageApplier, swarmDisseminator);
  }

  @Provides
  SwarmMessageSender provideSwarmMessageSender(SwarmClient swarmClient) {
    return swarmClient;
  }
}
