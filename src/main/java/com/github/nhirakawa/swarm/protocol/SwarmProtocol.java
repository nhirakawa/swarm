package com.github.nhirakawa.swarm.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.config.ConfigPath;
import com.github.nhirakawa.swarm.config.SwarmNode;
import com.github.nhirakawa.swarm.model.PingAckMessage;
import com.github.nhirakawa.swarm.model.PingMessage;
import com.github.nhirakawa.swarm.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;

public class SwarmProtocol {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmProtocol.class);

  private final SwarmClient swarmClient;
  private final List<SwarmNode> clusterNodes;
  private final Config config;

  private Instant lastPingSent = Instant.now();

  @Inject
  SwarmProtocol(SwarmClient swarmClient,
                Set<SwarmNode> clusterNodes,
                Config config) {
    this.swarmClient = swarmClient;
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.config = config;
  }

  public void start() {

  }

  public void handle(SwarmTimeoutMessage timeoutMessage) throws JsonProcessingException, InterruptedException {
    if (timeoutMessage.getTImestamp().isAfter(lastPingSent.plus(config.getDuration(ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath())))) {
      int randomIndex = ThreadLocalRandom.current().nextInt(0, clusterNodes.size());
      SwarmNode randomNode = clusterNodes.get(randomIndex);

      swarmClient.sendPing(randomNode).join();
      lastPingSent = Instant.now();
    }
  }

  public void handle(PingMessage pingMessage) throws JsonProcessingException, InterruptedException {
    swarmClient.sendPingAck(pingMessage.getSender());
  }

  public void handle(PingAckMessage pingAckMessage) {
    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());
  }

}
