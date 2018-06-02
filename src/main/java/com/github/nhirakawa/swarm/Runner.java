package com.github.nhirakawa.swarm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.guice.SwarmModule;
import com.github.nhirakawa.swarm.model.ImmutableUuidSwarmMessage;
import com.github.nhirakawa.swarm.model.SwarmMessageType;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class Runner {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

  public static void main(String... args) throws JsonProcessingException {
    LOG.info("{}", getBanner());

    Injector injector = Guice.createInjector(new SwarmModule());
    injector.getInstance(SwarmServer.class).start();

    System.exit(0);
  }

  private static String getBanner() {
    try {
      return Resources.toString(Resources.getResource("banner.txt"), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      return "swarm";
    }
  }
}
