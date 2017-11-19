package com.github.nhirakawa.swarm.guice;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.config.ConfigPath;
import com.github.nhirakawa.swarm.config.ConfigValidator;
import com.github.nhirakawa.swarm.config.ImmutableSwarmNode;
import com.github.nhirakawa.swarm.model.BaseSwarmMessage;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

public class SwarmModule extends AbstractModule {

  private static final TypeReference<Set<ImmutableSwarmNode>> SET_SWARM_NODE = new TypeReference<Set<ImmutableSwarmNode>>() {};

  @Override
  protected void configure() {
    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);

    bind(Config.class).toInstance(config);
    bind(ObjectMapper.class).toInstance(new ObjectMapper());
  }

  @Provides
  @Singleton
  @IncomingQueue
  BlockingQueue<BaseSwarmMessage> provideIncomingQueue() {
    return new ArrayBlockingQueue<>(100);
  }

  @Provides
  @Singleton
  @OutgoingQueue
  BlockingQueue<BaseSwarmMessage> provideOutgoingQueue() {
    return new ArrayBlockingQueue<>(100);
  }

  @Provides
  @Singleton
  ImmutableSwarmNode provideLocalSwarmNode(ObjectMapper objectMapper,
                                           Config config) throws IOException {
    return objectMapper.readValue(config.getObject(ConfigPath.LOCAL.getConfigPath()).render(ConfigRenderOptions.concise().setJson(true)), ImmutableSwarmNode.class);
  }

  @Provides
  @Singleton
  Set<ImmutableSwarmNode> provideSwarmClusterNodes(ObjectMapper objectMapper,
                                                   Config config) throws IOException {
    return objectMapper.readValue(config.getObject(ConfigPath.CLUSTER.getConfigPath()).render(ConfigRenderOptions.concise().setJson(true)), SET_SWARM_NODE);
  }
}
