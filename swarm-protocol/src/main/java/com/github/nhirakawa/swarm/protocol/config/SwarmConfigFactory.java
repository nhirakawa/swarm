package com.github.nhirakawa.swarm.protocol.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import java.time.Duration;
import java.util.Set;

public final class SwarmConfigFactory {
  private static final TypeReference<Set<SwarmNode>> SET_SWARM_NODE = new TypeReference<>() {};

  public static SwarmConfig get(Config config) throws JsonProcessingException {
    validate(config);

    Duration protocolPeriod = config.getDuration(
      ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath()
    );
    Duration messageTimeout = config.getDuration(
      ConfigPath.SWARM_MESSAGE_TIMEOUT.getConfigPath()
    );
    Duration protocolTick = config.getDuration(
      ConfigPath.SWARM_PROTOCOL_TICK.getConfigPath()
    );
    int failureSubGroup = config.getInt(
      ConfigPath.SWARM_FAILURE_SUBGROUP.getConfigPath()
    );
    Set<SwarmNode> clusterNodes = ObjectMapperWrapper
      .instance()
      .readValue(
        config
          .getList(ConfigPath.CLUSTER_NODES.getConfigPath())
          .render(ConfigRenderOptions.concise()),
        SET_SWARM_NODE
      );
    SwarmNode localSwarmNode = ObjectMapperWrapper
      .instance()
      .readValue(
        config
          .getObject(ConfigPath.LOCAL_NODE.getConfigPath())
          .render(ConfigRenderOptions.concise()),
        SwarmNode.class
      );
    boolean isEntireClusterLocal = config.getBoolean(
      ConfigPath.RUN_ENTIRE_CLUSTER_LOCALLY.getConfigPath()
    );
    boolean isDebugEnabled = config.getBoolean(
      ConfigPath.DEBUG_ENABLED.getConfigPath()
    );
    int failureInjectionPercent = config.getInt(
      ConfigPath.FAILURE_INJECTION_PERCENT.getConfigPath()
    );
    int swarmStateBufferSize = config.getInt(
      ConfigPath.SWARM_STATE_BUFFER_SIZE.getConfigPath()
    );

    return SwarmConfig
      .builder()
      .setProtocolPeriod(protocolPeriod)
      .setMessageTimeout(messageTimeout)
      .setProtocolTick(protocolTick)
      .setFailureSubGroup(failureSubGroup)
      .setClusterNodes(clusterNodes)
      .setLocalNode(localSwarmNode)
      .setEntireClusterLocal(isEntireClusterLocal)
      .setDebugEnabled(isDebugEnabled)
      .setFailureInjectionPercent(failureInjectionPercent)
      .setSwarmStateBufferSize(swarmStateBufferSize)
      .build();
  }

  private static void validate(Config config) {
    ConfigValidator.validate(config);
  }

  private SwarmConfigFactory() {
    throw new UnsupportedOperationException();
  }
}
