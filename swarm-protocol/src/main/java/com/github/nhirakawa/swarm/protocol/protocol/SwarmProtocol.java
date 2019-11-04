package com.github.nhirakawa.swarm.protocol.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;

public class SwarmProtocol {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmProtocol.class);

  private final List<SwarmNode> clusterNodes;
  private final Config config;

  private Instant lastPingSent = Instant.now();

  @Inject
  SwarmProtocol(Set<SwarmNode> clusterNodes, Config config) {
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.config = config;
  }

  public void start() {

  }

  public Optional<?> handle(SwarmTimeoutMessage timeoutMessage) throws JsonProcessingException, InterruptedException {
    if (timeoutMessage.getTImestamp().isAfter(lastPingSent.plus(config.getDuration(ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath())))) {
      int randomIndex = ThreadLocalRandom.current().nextInt(0, clusterNodes.size());
      SwarmNode randomNode = clusterNodes.get(randomIndex);

//      swarmClient.sendPing(randomNode).join();
      lastPingSent = Instant.now();
    }

    return Optional.empty();
  }

  public void handle(PingMessage pingMessage) throws JsonProcessingException, InterruptedException {
//    swarmClient.sendPingAck(pingMessage.getSender());
  }

  public void handle(PingAckMessage pingAckMessage) {
    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());
  }

}
