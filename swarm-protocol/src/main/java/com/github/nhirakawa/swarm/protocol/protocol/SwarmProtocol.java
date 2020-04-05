package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingResponse;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponses;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SwarmProtocol {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmProtocol.class
  );

  private final List<SwarmNode> clusterNodes;
  private final SwarmNode localSwarmNode;
  private final Config config;

  private Instant lastPingSent = Instant.now();

  @Inject
  SwarmProtocol(
    Set<SwarmNode> clusterNodes,
    SwarmNode localSwarmNode,
    Config config
  ) {
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.localSwarmNode = localSwarmNode;
    this.config = config;
  }

  void start() {}

  TimeoutResponse handle(SwarmTimeoutMessage timeoutMessage) {
    if (
      timeoutMessage
        .getTImestamp()
        .isAfter(
          lastPingSent.plus(
            config.getDuration(ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath())
          )
        )
    ) {
      int randomIndex = ThreadLocalRandom
        .current()
        .nextInt(0, clusterNodes.size());
      SwarmNode randomNode = clusterNodes.get(randomIndex);

      lastPingSent = Instant.now();

      return TimeoutResponse.builder().setTargetNode(randomNode).build();
    }

    return TimeoutResponses.empty();
  }

  PingAckMessage handle(PingMessage pingMessage) {
    return PingAckMessage.builder().setSender(localSwarmNode).build();
  }

  PingAckResponse handle(PingAckMessage pingAckMessage) {
    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());

    return PingAckResponse.builder().setTimestamp(Instant.now()).build();
  }
}
