package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.ConfigValidator;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
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

    SwarmService swarmService = DaggerSwarmNettyComponent
      .builder()
      .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
      .build()
      .buildService();

    swarmService.run();
  }
}
