package com.github.nhirakawa.swarm.protocol.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponses;
import com.github.nhirakawa.swarm.protocol.model.local.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.local.PingResponse;
import com.github.nhirakawa.swarm.protocol.model.local.TimeoutResponse;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;

public class SwarmProtocol {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmProtocol.class);

  private final List<SwarmNode> clusterNodes;
  private final SwarmNode localSwarmNode;
  private final Config config;

  private Instant lastPingSent = Instant.now();

  @Inject
  SwarmProtocol(Set<SwarmNode> clusterNodes, SwarmNode localSwarmNode, Config config) {
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.localSwarmNode = localSwarmNode;
    this.config = config;
  }

  public void start() {

  }

  public TimeoutResponse handle(SwarmTimeoutMessage timeoutMessage) {
    if (timeoutMessage.getTImestamp().isAfter(lastPingSent.plus(config.getDuration(ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath())))) {
      int randomIndex = ThreadLocalRandom.current().nextInt(0, clusterNodes.size());
      SwarmNode randomNode = clusterNodes.get(randomIndex);

      lastPingSent = Instant.now();

      return TimeoutResponse.builder()
          .setTargetNode(randomNode)
          .build();
    }

    return TimeoutResponses.empty();
  }

  public PingResponse handle(PingMessage pingMessage) {
    return PingResponse.builder()
        .setLocalSwarmNode(localSwarmNode)
        .setTargetNode(pingMessage.getSender())
        .build();
  }

  public PingAckResponse handle(PingAckMessage pingAckMessage) {
    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());

    return PingAckResponse.builder()
        .setTimestamp(Instant.now())
        .build();
  }

}
