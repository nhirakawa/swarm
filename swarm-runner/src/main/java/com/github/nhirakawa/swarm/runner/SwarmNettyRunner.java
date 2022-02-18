package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.config.ConfigValidator;
import com.github.nhirakawa.swarm.runner.config.SwarmConfigFactory;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmNettyRunner {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmNettyRunner.class
  );

  public static void main(String... args) throws Throwable {
    LOG.info("{}", getBanner());

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
}
