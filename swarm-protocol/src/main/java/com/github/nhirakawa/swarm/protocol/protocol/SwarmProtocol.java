package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
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
import com.github.nhirakawa.swarm.protocol.model.timeout.EmptyTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.PingProxyTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.PingTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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

  private final SwarmConfig swarmConfig;
  private final SwarmStateBuffer swarmStateBuffer;
  private final Clock clock;
  private final List<SwarmNode> clusterNodesList;

  @Inject
  SwarmProtocol(
    SwarmConfig swarmConfig,
    SwarmStateBuffer swarmStateBuffer,
    Clock clock
  ) {
    this.swarmConfig = swarmConfig;
    this.swarmStateBuffer = swarmStateBuffer;
    this.clock = clock;
    this.clusterNodesList = ImmutableList.copyOf(swarmConfig.getClusterNodes());
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
            .plus(swarmConfig.getProtocolPeriod())
        )
    ) {
      SwarmNode randomNode = getRandomNode();
      String nextProtocolPeriodId = UUID.randomUUID().toString();

      LastAckRequest lastAckRequest = LastAckRequest
        .builder()
        .setProtocolPeriodId(nextProtocolPeriodId)
        .setSwarmNode(randomNode)
        .setTimestamp(now)
        .build();

      SwarmState updatedSwarmState = SwarmState
        .builder()
        .from(swarmState)
        .setLastProtocolPeriodStarted(now)
        .setLastProtocolPeriodId(nextProtocolPeriodId)
        .setTimestamp(now)
        .setLastAckRequest(lastAckRequest)
        .setLastProxySentTimestamp(Optional.empty())
        .build();

      swarmStateBuffer.add(updatedSwarmState);

      return PingTimeoutResponse
        .builder()
        .setProtocolId(swarmState.getLastProtocolPeriodId())
        .setSwarmNode(randomNode)
        .build();
    }

    Instant earliestValidAckRequestTimestamp = now.minus(
      swarmConfig.getMessageTimeout()
    );

    if (swarmState.getLastAckRequest().isEmpty()) {
      return EmptyTimeoutResponse.instance();
    }

    LastAckRequest lastAckRequest = swarmState
      .getLastAckRequest()
      .orElseThrow();

    if (
      lastAckRequest.getTimestamp().isAfter(earliestValidAckRequestTimestamp)
    ) {
      return EmptyTimeoutResponse.instance();
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
      .setLastProxySentTimestamp(now)
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    int failureSubgroup = swarmConfig.getFailureSubGroup();
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

    return PingProxyTimeoutResponse
      .builder()
      .setProtocolId(swarmState.getLastProtocolPeriodId())
      .setProxyTargets(proxyTargets)
      .build();
  }

  private Collection<SwarmNode> getRandomNodes(int number) {
    Preconditions.checkArgument(
      number > 0,
      "Must be greater than 0 (%s)",
      number
    );
    Preconditions.checkArgument(
      number <= clusterNodesList.size(),
      "Cannot request more than %s random nodes (%s)",
      clusterNodesList.size(),
      number
    );

    if (number == clusterNodesList.size()) {
      return clusterNodesList;
    }

    Set<SwarmNode> randomNodes = new HashSet<>(number);

    while (randomNodes.size() < number) {
      int randomIndex = ThreadLocalRandom
        .current()
        .nextInt(0, clusterNodesList.size());

      randomNodes.add(clusterNodesList.get(randomIndex));
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
      if (
        pingAckMessage.getProxyFor().get().equals(swarmConfig.getLocalNode())
      ) {
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
