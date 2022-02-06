package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.handler.codec.compression.Brotli;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmLocalClusterRunner {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmLocalClusterRunner.class
  );
  private static final ExecutorService EXECUTOR = buildExecutor();

  public static void main(String... args) throws Throwable {
    LOG.info("{}", BannerUtil.getOrDefault("swarm-local-cluster"));

    Brotli.ensureAvailability();

    Config nodesConfig = ConfigFactory.load("cluster.conf");

    for (Config nodeConfig : nodesConfig.getConfigList("nodes")) {
      Config realConfig = ConfigFactory
        .load(nodeConfig)
        .withFallback(ConfigFactory.load());

      SwarmConfig swarmConfig = SwarmConfigFactory.get(realConfig);

      SwarmService swarmService = DaggerSwarmNettyComponent
        .builder()
        .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
        .build()
        .buildService();

      EXECUTOR.execute(swarmService::run);
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
