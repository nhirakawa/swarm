package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingProxyRequest;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponses;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotThreadSafe
class SwarmProtocol {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmProtocol.class
  );

  private final List<SwarmNode> clusterNodes;
  private final SwarmNode localSwarmNode;
  private final Config config;
  private final SwarmStateBuffer swarmStateBuffer;
  private final Clock clock;

  @Inject
  SwarmProtocol(
    Set<SwarmNode> clusterNodes,
    SwarmNode localSwarmNode,
    Config config,
    SwarmStateBuffer swarmStateBuffer,
    Clock clock
  ) {
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.localSwarmNode = localSwarmNode;
    this.config = config;
    this.swarmStateBuffer = swarmStateBuffer;
    this.clock = clock;
  }

  void start() {}

  TimeoutResponse handle(SwarmTimeoutMessage timeoutMessage) {
    SwarmState swarmState = swarmStateBuffer.getCurrent();
    Instant now = clock.instant();

    if (
      timeoutMessage
        .getTImestamp()
        .isAfter(
          swarmState
            .getLastProtocolPeriodStarted()
            .plus(
              config.getDuration(
                ConfigPath.SWARM_PROTOCOL_PERIOD.getConfigPath()
              )
            )
        )
    ) {
      int randomIndex = ThreadLocalRandom
        .current()
        .nextInt(0, clusterNodes.size());

      SwarmNode randomNode = clusterNodes.get(randomIndex);

      SwarmState updatedSwarmState = SwarmState
        .builder()
        .from(swarmState)
        .setLastProtocolPeriodStarted(now)
        .setTimestamp(now)
        .putLastAckRequestBySwarmNode(randomNode, now)
        .build();

      swarmStateBuffer.add(updatedSwarmState);

      return TimeoutResponse.builder().setTargetNode(randomNode).build();
    }

    Instant earliestValidAckRequestTimestamp = now.minus(
      config.getDuration(ConfigPath.SWARM_MESSAGE_TIMEOUT.getConfigPath())
    );

    Map<SwarmNode, Instant> timedOutSwarmNodes = Maps.filterValues(
      swarmState.getLastAckRequestBySwarmNode(),
      lastAckRequestTimestamp -> lastAckRequestTimestamp.isBefore(
        earliestValidAckRequestTimestamp
      )
    );

    if (timedOutSwarmNodes.isEmpty()) {
      return TimeoutResponses.empty();
    }

    for (Entry<SwarmNode, Instant> entry : timedOutSwarmNodes.entrySet()) {
      SwarmNode swarmNode = entry.getKey();
      Instant timestamp = entry.getValue();

      LOG.debug(
        "{} was sent ack request at {} but has not responded",
        swarmNode,
        timestamp
      );
    }

    Map<SwarmNode, Instant> filteredTimestamps = Maps.filterKeys(
      swarmState.getLastAckRequestBySwarmNode(),
      Predicates.not(timedOutSwarmNodes::containsKey)
    );

    Set<SwarmNode> alreadyFailedSwarmNodes = Maps
      .filterValues(
        swarmState.getMemberStatusBySwarmNode(),
        MemberStatus.FAILED::equals
      )
      .keySet();

    Set<SwarmNode> failedSwarmNodes = Sets.union(
      timedOutSwarmNodes.keySet(),
      alreadyFailedSwarmNodes
    );

    Map<SwarmNode, MemberStatus> updatedMemberStatuses = Maps.toMap(
      failedSwarmNodes,
      ignored -> MemberStatus.FAILED
    );

    SwarmState updatedSwarmState = SwarmState
      .builder()
      .from(swarmState)
      .setTimestamp(now)
      .setMemberStatusBySwarmNode(updatedMemberStatuses)
      .setLastAckRequestBySwarmNode(filteredTimestamps)
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    return TimeoutResponses.empty();
  }

  PingAckMessage handle(PingMessage ignored) {
    return PingAckMessage.builder().setSender(localSwarmNode).build();
  }

  PingAckResponse handle(PingAckMessage pingAckMessage) {
    SwarmState currentSwarmState = swarmStateBuffer.getCurrent();

    Instant now = clock.instant();

    PingAckResponse pingAckResponse = PingAckResponse
      .builder()
      .setTimestamp(now)
      .build();

    if (
      !currentSwarmState
        .getLastAckRequestBySwarmNode()
        .containsKey(pingAckMessage.getSender())
    ) {
      LOG.warn("No outstanding ping for {}", pingAckMessage.getSender());
      return pingAckResponse;
    }

    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());

    Map<SwarmNode, Instant> updatedOutstandingPingAckBySwarmNode = Maps.filterKeys(
      currentSwarmState.getLastAckRequestBySwarmNode(),
      swarmNode -> !swarmNode.equals(pingAckMessage.getSender())
    );

    SwarmState updatedSwarmState = SwarmState
      .builder()
      .from(currentSwarmState)
      .setTimestamp(now)
      .setLastAckRequestBySwarmNode(updatedOutstandingPingAckBySwarmNode)
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    return PingAckResponse.builder().setTimestamp(clock.instant()).build();
  }

  public PingAckResponse handle(PingProxyRequest pingProxyRequest) {
    throw new UnsupportedOperationException();
  }
}
