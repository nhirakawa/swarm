package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.guice.SwarmNettyModule;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.guice.SwarmConfigModule;
import com.github.nhirakawa.swarm.protocol.guice.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.ConfigValidator;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(
  name = "netty",
  description = "Run a single Swarm node using netty for transport"
)
public class SwarmNettyRunner implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmNettyRunner.class
  );

  public Integer call() throws Exception {
    LOG.info(
      "\n{}",
      BannerUtil.getOrDefault("swarm-netty-banner.txt", "swarm")
    );

    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);

    SwarmConfig swarmConfig = SwarmConfigFactory.get(config);

    SwarmConfigModule swarmConfigModule = new SwarmConfigModule(swarmConfig);

    Injector injector = Guice.createInjector(
      swarmConfigModule,
      new SwarmNettyModule(),
      new SwarmProtocolModule()
    );

    SwarmService swarmService = injector.getInstance(SwarmService.class);

    swarmService.startAsync().awaitRunning(Duration.ofSeconds(1));

    swarmService.awaitTerminated();

    return 0;
  }
}
