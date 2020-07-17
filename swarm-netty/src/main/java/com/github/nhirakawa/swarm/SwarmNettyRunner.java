package com.github.nhirakawa.swarm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.ConfigValidator;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfigFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmNettyRunner {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmNettyRunner.class
  );
  private static final ExecutorService EXECUTOR = buildExecutor();
  private static final TypeReference<Set<SwarmNode>> SET_SWARM_NODE = new TypeReference<Set<SwarmNode>>() {};

  public static void main(String... args) throws IOException {
    LOG.info("{}", getBanner());

    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);

    if (
      config.getBoolean(ConfigPath.DEBUG_ENABLED.getConfigPath()) &&
      config.getBoolean(ConfigPath.RUN_ENTIRE_CLUSTER_LOCALLY.getConfigPath())
    ) {
      runCluster(config);
    } else {
      runSingle(config);
    }
  }

  private static void runSingle(Config config) throws IOException {
    SwarmConfig swarmConfig = SwarmConfigFactory.get(config);

    SwarmServer swarmServer = DaggerSwarmComponent
      .builder()
      .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
      .build()
      .buildServer();

    swarmServer.start();
  }

  private static void runCluster(Config config) throws IOException {
    Set<SwarmNode> clusterNodes = ObjectMapperWrapper
      .instance()
      .readValue(
        config
          .getList(ConfigPath.CLUSTER_NODES.getConfigPath())
          .render(ConfigRenderOptions.concise()),
        SET_SWARM_NODE
      );

    for (SwarmNode swarmNode : clusterNodes) {
      Config nodeSpecificConfig = ConfigFactory
        .parseMap(
          ImmutableMap.of(
            "wilson.localNode.host",
            swarmNode.getHost(),
            "wilson.localNode.port",
            swarmNode.getPort()
          )
        )
        .withFallback(config);

      SwarmConfig swarmConfig = SwarmConfigFactory.get(nodeSpecificConfig);

      SwarmServer swarmServer = DaggerSwarmComponent
        .builder()
        .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
        .build()
        .buildServer();

      EXECUTOR.execute(swarmServer::start);
    }
  }

  private static String getBanner() {
    try {
      return Resources.toString(
        Resources.getResource("banner.txt"),
        StandardCharsets.UTF_8
      );
    } catch (IOException ignored) {
      return "swarm";
    }
  }

  private static ExecutorService buildExecutor() {
    return Executors.newFixedThreadPool(
      4,
      new ThreadFactoryBuilder()
        .setNameFormat("swarm-runner-%s")
        .setUncaughtExceptionHandler(
          (ignored, t) -> LOG.error("Caught exception", t)
        )
        .build()
    );
  }
}
