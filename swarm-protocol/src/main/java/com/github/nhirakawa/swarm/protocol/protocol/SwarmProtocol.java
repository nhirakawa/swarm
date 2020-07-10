package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAck;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAcks;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingResponse;
import com.github.nhirakawa.swarm.protocol.model.PingResponses;
import com.github.nhirakawa.swarm.protocol.model.ProxyTarget;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargets;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponses;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
  private final Config config;
  private final SwarmStateBuffer swarmStateBuffer;
  private final SwarmNode localSwarmNode;
  private final Clock clock;

  @Inject
  SwarmProtocol(
    Set<SwarmNode> clusterNodes,
    Config config,
    SwarmStateBuffer swarmStateBuffer,
    SwarmNode localSwarmNode,
    Clock clock
  ) {
    this.clusterNodes = ImmutableList.copyOf(clusterNodes);
    this.config = config;
    this.swarmStateBuffer = swarmStateBuffer;
    this.localSwarmNode = localSwarmNode;
    this.clock = clock;
  }

  TimeoutResponse handle(SwarmTimeoutMessage timeoutMessage) {
    SwarmState swarmState = swarmStateBuffer.getCurrent();
    Instant now = clock.instant();

    if (
      timeoutMessage
        .getTimestamp()
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
      SwarmNode randomNode = getRandomNode();

      LastAckRequest lastAckRequest = LastAckRequest
        .builder()
        .setProtocolPeriodId(swarmState.getLastProtocolPeriodId())
        .setSwarmNode(randomNode)
        .setTimestamp(now)
        .build();

      SwarmState updatedSwarmState = SwarmState
        .builder()
        .from(swarmState)
        .setLastProtocolPeriodStarted(now)
        .setTimestamp(now)
        .setLastAckRequest(lastAckRequest)
        .build();

      swarmStateBuffer.add(updatedSwarmState);

      return TimeoutResponses.ping(randomNode);
    }

    Instant earliestValidAckRequestTimestamp = now.minus(
      config.getDuration(ConfigPath.SWARM_MESSAGE_TIMEOUT.getConfigPath())
    );

    LastAckRequest lastAckRequest = swarmState
      .getLastAckRequest()
      .orElseThrow();

    if (
      lastAckRequest.getTimestamp().isAfter(earliestValidAckRequestTimestamp)
    ) {
      return TimeoutResponses.empty();
    }

    LOG.debug(
      "{} was sent ACK request at {} but has not responded",
      lastAckRequest.getSwarmNode(),
      lastAckRequest.getTimestamp()
    );

    Set<SwarmNode> alreadyFailedSwarmNodes = Maps
      .filterValues(
        swarmState.getMemberStatusBySwarmNode(),
        MemberStatus.FAILED::equals
      )
      .keySet();

    Set<SwarmNode> failedSwarmNodes = Sets.union(
      Collections.singleton(lastAckRequest.getSwarmNode()),
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
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    int failureSubgroup = config.getInt(
      ConfigPath.SWARM_FAILURE_SUBGROUP.getConfigPath()
    );

    List<ProxyTarget> proxyTargetsList = getRandomNodes(failureSubgroup)
      .stream()
      .map(
        swarmNode -> ProxyTarget
          .builder()
          .setTargetNode(lastAckRequest.getSwarmNode())
          .setProxyNode(swarmNode)
          .build()
      )
      .collect(ImmutableList.toImmutableList());

    ProxyTargets proxyTargets = ProxyTargets
      .builder()
      .setProxyTargets(proxyTargetsList)
      .build();

    return TimeoutResponses.proxy(proxyTargets);
  }

  private Collection<SwarmNode> getRandomNodes(int number) {
    Preconditions.checkArgument(
      number > 0,
      "Must be greater than 0 (%s)",
      number
    );
    Preconditions.checkArgument(
      number <= clusterNodes.size(),
      "Cannot request more than %s random nodes (%s)",
      clusterNodes.size(),
      number
    );

    if (number == clusterNodes.size()) {
      return clusterNodes;
    }

    Set<SwarmNode> randomNodes = new HashSet<>(number);

    while (randomNodes.size() < number) {
      int randomIndex = ThreadLocalRandom
        .current()
        .nextInt(0, clusterNodes.size());

      randomNodes.add(clusterNodes.get(randomIndex));
    }

    return randomNodes;
  }

  private SwarmNode getRandomNode() {
    return Iterables.getOnlyElement(getRandomNodes(1));
  }

  PingResponse handle(PingMessage pingMessage) {
    if (
      pingMessage.getProxyFor().isPresent() &&
      pingMessage.getProxyFor().get().equals(pingMessage.getSender())
    ) {
      return PingResponses.proxy(pingMessage.getProxyFor().get());
    }

    return PingResponses.ack();
  }

  PingAck handle(PingAckMessage pingAckMessage) {
    if (pingAckMessage.getProxyFor().isPresent()) {
      if (pingAckMessage.getProxyFor().get().equals(localSwarmNode)) {
        // do nothing
      } else {
        return PingAcks.proxy(pingAckMessage.getProxyFor().get());
      }
    }

    SwarmState currentSwarmState = swarmStateBuffer.getCurrent();

    Instant now = clock.instant();

    if (!currentSwarmState.getLastAckRequest().isPresent()) {
      LOG.info("No outstanding ping request");
      return PingAcks.noOutstandingPingRequest();
    }

    LastAckRequest lastAckRequest = currentSwarmState.getLastAckRequest().get();

    if (
      !pingAckMessage
        .getProtocolPeriodId()
        .equals(lastAckRequest.getProtocolPeriodId())
    ) {
      LOG.info(
        "{} does not match current protocol period ID ({})",
        pingAckMessage.getProtocolPeriodId(),
        lastAckRequest.getProtocolPeriodId()
      );
      return PingAcks.invalidProtocolPeriod();
    }

    if (!pingAckMessage.getSender().equals(lastAckRequest.getSwarmNode())) {
      LOG.info(
        "{} does not match last ack request ({})",
        pingAckMessage.getSender(),
        lastAckRequest.getSwarmNode()
      );
      return PingAcks.invalidSender();
    }

    LOG.info("{} has acknowledged ping", pingAckMessage.getSender());

    SwarmState updatedSwarmState = SwarmState
      .builder()
      .from(currentSwarmState)
      .setTimestamp(now)
      .setLastAckRequest(Optional.empty())
      .setLastProtocolPeriodId(UUID.randomUUID().toString())
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    return PingAcks.ack(clock.instant());
  }
}
