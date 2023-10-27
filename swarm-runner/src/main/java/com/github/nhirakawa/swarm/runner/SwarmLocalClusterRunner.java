package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.guice.SwarmNettyModule;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.guice.SwarmConfigModule;
import com.github.nhirakawa.swarm.protocol.guice.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
import com.github.nhirakawa.swarm.runner.util.NamedService;
import com.github.nhirakawa.swarm.runner.util.ServiceObserver;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "local")
public class SwarmLocalClusterRunner implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmLocalClusterRunner.class
  );
  private static final ExecutorService EXECUTOR = buildExecutor();

  public Integer call() throws Exception {
    LOG.info(
      "\n{}",
      BannerUtil.getOrDefault("swarm-local-banner.txt", "swarm-local-cluster")
    );

    Config nodesConfig = ConfigFactory.load("cluster.conf");

    List<NamedService> services = new ArrayList<>();

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

      swarmService.startAsync().awaitRunning(Duration.ofSeconds(1));

      services.add(swarmService);
    }

    ServiceObserver serviceObserver = new ServiceObserver(services);

    serviceObserver.startAsync().awaitRunning(Duration.ofSeconds(1));

    serviceObserver.awaitTerminated();

    LOG.info("ServiceObserver is terminated");

    return 0;
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
