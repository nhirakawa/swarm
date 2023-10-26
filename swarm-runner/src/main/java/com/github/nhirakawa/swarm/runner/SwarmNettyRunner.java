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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmNettyRunner {

  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmNettyRunner.class
  );

  public static void main(String... args) throws Throwable {
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

    swarmService.run();
  }
}
