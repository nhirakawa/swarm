package com.github.nhirakawa.swarm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;

public class Runner {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

  public static void main(String... args) throws JsonProcessingException {
    LOG.info("{}", getBanner());

    SwarmComponent swarmComponent = DaggerSwarmComponent.create();
    swarmComponent.buildServer().start();

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
