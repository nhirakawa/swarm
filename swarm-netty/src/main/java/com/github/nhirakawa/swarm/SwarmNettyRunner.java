package com.github.nhirakawa.swarm;

import com.github.nhirakawa.swarm.protocol.config.ConfigValidator;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfigFactory;
import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;
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

  public static void main(String... args) throws IOException {
    LOG.info("{}", getBanner());

    Config config = ConfigFactory.load();
    ConfigValidator.validate(config);

    SwarmConfig swarmConfig = SwarmConfigFactory.get(config);

    SwarmServer swarmServer = DaggerSwarmComponent
      .builder()
      .swarmProtocolModule(new SwarmProtocolModule(swarmConfig))
      .build()
      .buildServer();

    swarmServer.start();
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
