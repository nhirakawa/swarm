package com.github.nhirakawa.swarm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.nhirakawa.swarm.config.ConfigPath;
import com.github.nhirakawa.swarm.config.ConfigValidator;
import com.github.nhirakawa.swarm.config.SwarmNode;
import com.github.nhirakawa.swarm.dagger.SwarmDaggerModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

public class Runner {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);
  private static final ExecutorService EXECUTOR = buildExecutor();
  private static final TypeReference<Set<SwarmNode>> SET_SWARM_NODE = new TypeReference<Set<SwarmNode>>() {};

  public static void main(String... args) throws IOException {
    LOG.info("{}", getBanner());

    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);

    if (config.getBoolean(ConfigPath.RUN_ENTIRE_CLUSTER_LOCALLY.getConfigPath())) {
      runCluster(config);
    } else {
      runSingle(config);
    }

    EXECUTOR.shutdown();

    System.exit(0);
  }

  private static void runSingle(Config config) throws IOException {
    SwarmNode localSwarmNode = ObjectMapperWrapper.instance().readValue(
        config.getObject(ConfigPath.LOCAL_NODE.getConfigPath()).render(ConfigRenderOptions.concise()),
        SwarmNode.class
    );
    Set<SwarmNode> clusterNodes = ObjectMapperWrapper.instance().readValue(
        config.getList(ConfigPath.CLUSTER_NODES.getConfigPath()).render(ConfigRenderOptions.concise()),
        SET_SWARM_NODE
    );

    SwarmComponent swarmComponent = DaggerSwarmComponent.builder()
        .swarmDaggerModule(new SwarmDaggerModule(config, localSwarmNode, clusterNodes))
        .build();

    swarmComponent.buildServer().start();
  }

  private static void runCluster(Config config) throws IOException {
    Set<SwarmNode> clusterNodes = ObjectMapperWrapper.instance().readValue(
        config.getList(ConfigPath.CLUSTER_NODES.getConfigPath()).render(ConfigRenderOptions.concise()),
        SET_SWARM_NODE
    );

    List<CompletableFuture<?>> futures = new ArrayList<>();

    for (SwarmNode swarmNode : clusterNodes) {
      Config nodeSpecificConfig = ConfigFactory.parseMap(
          ImmutableMap.of(
              "wilson.localNode.host", swarmNode.getHost(),
              "wilson.localNode.port", swarmNode.getPort()
          )
      ).withFallback(config);

      SwarmComponent swarmComponent = DaggerSwarmComponent.builder()
          .swarmDaggerModule(new SwarmDaggerModule(nodeSpecificConfig, swarmNode, clusterNodes))
          .build();

      CompletableFuture<?> future = CompletableFuture.runAsync(() -> swarmComponent.buildServer().start(), EXECUTOR);
      future.exceptionally(t -> {
        LOG.error("Caught exception", t);
        return null;
      });
      futures.add(future);
    }

    for (CompletableFuture<?> future : futures) {
      future.join();
    }
  }

  private static String getBanner() {
    try {
      return Resources.toString(Resources.getResource("banner.txt"), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      return "swarm";
    }
  }

  private static ExecutorService buildExecutor() {
    return Executors.newFixedThreadPool(
        4,
        new ThreadFactoryBuilder()
            .setNameFormat("swarm-runner-%s")
            .setUncaughtExceptionHandler((ignored, t) -> LOG.error("Caught exception", t))
            .build()
    );
  }
}
