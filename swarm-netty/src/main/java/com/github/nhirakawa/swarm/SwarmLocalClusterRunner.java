package com.github.nhirakawa.swarm;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfigFactory;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmLocalClusterRunner {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmLocalClusterRunner.class
  );
  private static final ExecutorService EXECUTOR = buildExecutor();

  public static void main(String... args) throws IOException {
    LOG.info("{}", BannerUtil.getOrDefault("swarm-local-cluster"));

    Config nodesConfig = ConfigFactory.load("cluster.conf");

    for (Config nodeConfig : nodesConfig.getConfigList("nodes")) {
      Config realConfig = ConfigFactory
        .load(nodeConfig)
        .withFallback(ConfigFactory.load());

      SwarmConfig swarmConfig = SwarmConfigFactory.get(realConfig);

      LOG.trace(
        "Config for node {} - {}",
        swarmConfig.getLocalNode(),
        realConfig.root().render(ConfigRenderOptions.defaults())
      );

      SwarmServer swarmServer = DaggerSwarmComponent
        .builder()
        .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
        .build()
        .buildServer();

      EXECUTOR.execute(swarmServer::start);
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
