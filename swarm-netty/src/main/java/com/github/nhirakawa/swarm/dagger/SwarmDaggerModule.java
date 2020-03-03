package com.github.nhirakawa.swarm.dagger;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import dagger.Module;
import dagger.Provides;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Set;
import javax.inject.Singleton;

@Module
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
  ScheduledExecutorService provideScheduledExecutorService() {
    return Executors.newScheduledThreadPool(4);
  }
}
