package com.github.nhirakawa.swarm.dagger;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.inject.Singleton;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.config.ConfigPath;
import com.github.nhirakawa.swarm.config.ConfigValidator;
import com.github.nhirakawa.swarm.config.SwarmNode;
import com.github.nhirakawa.swarm.model.BaseSwarmMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import dagger.Module;
import dagger.Provides;

@Module
public class SwarmDaggerModule {

  private static final TypeReference<Set<SwarmNode>> SET_SWARM_NODE = new TypeReference<Set<SwarmNode>>() {};

  @Provides
  @Singleton
  static Config provideConfig() {
    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);
    return config;
  }

  @Provides
  @Singleton
  static ObjectMapper provideObjectMapper() {
    return new ObjectMapper();
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
  static SwarmNode provideLocalSwarmNode(ObjectMapper objectMapper,
                                         Config config) {
    try {
      return objectMapper.readValue(
          config.getObject(
              ConfigPath.LOCAL.getConfigPath()
          )
              .render(ConfigRenderOptions.concise().setJson(true)),
          SwarmNode.class
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Provides
  @Singleton
  Set<SwarmNode> provideSwarmClusterNodes(ObjectMapper objectMapper,
                                          Config config) {
    try {
      return objectMapper.readValue(
          config.getObject(
              ConfigPath.CLUSTER.getConfigPath()
          )
              .render(ConfigRenderOptions.concise().setJson(true)),
          SET_SWARM_NODE
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
