package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.guice.SwarmNettyModule;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.guice.SwarmConfigModule;
import com.github.nhirakawa.swarm.protocol.guice.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmLocalClusterRunner {

  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmLocalClusterRunner.class
  );
  private static final ExecutorService EXECUTOR = buildExecutor();

  public static void main(String... args) throws Throwable {
    LOG.info(
      "\n{}",
      BannerUtil.getOrDefault("swarm-local-banner.txt", "swarm-local-cluster")
    );

    Config nodesConfig = ConfigFactory.load("cluster.conf");

    for (Config nodeConfig : nodesConfig.getConfigList("nodes")) {
      Config realConfig = ConfigFactory
        .load(nodeConfig)
        .withFallback(ConfigFactory.load());

      SwarmConfig swarmConfig = SwarmConfigFactory.get(realConfig);

      SwarmConfigModule swarmConfigModule = new SwarmConfigModule(swarmConfig);

      Injector injector = Guice.createInjector(
        swarmConfigModule,
        new SwarmProtocolModule(),
        new SwarmNettyModule()
      );

      SwarmService swarmService = injector.getInstance(SwarmService.class);

      EXECUTOR.execute(swarmService::run);
    }
  }

  private static ExecutorService buildExecutor() {
    return Executors.newFixedThreadPool(
      4,
      new ThreadFactoryBuilder()
        .setNameFormat("swarm-runner-%s")
        .setUncaughtExceptionHandler((ignored, t) ->
          LOG.error("Caught exception", t)
        )
        .build()
    );
  }
}
