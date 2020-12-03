package com.github.nhirakawa.swarm.protocol.protocol;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.ProxyTarget;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargets;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.ack.AcknowledgePing;
import com.github.nhirakawa.swarm.protocol.model.ack.AcknowledgeProxy;
import com.github.nhirakawa.swarm.protocol.model.ack.PingAck;
import com.github.nhirakawa.swarm.protocol.model.ack.PingAckError;
import com.github.nhirakawa.swarm.protocol.model.ping.PingProxy;
import com.github.nhirakawa.swarm.protocol.model.ping.PingResponse;
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
import com.hubspot.algebra.Result;

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

      LOG.debug(
        "Protocol transitioning from ID {} to ID {}",
        swarmState.getLastProtocolPeriodId(),
        nextProtocolPeriodId
      );

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

      LOG.debug("Timeout reached - sending ping request to {}", randomNode);

      return PingTimeoutResponse
        .builder()
        .setProtocolId(nextProtocolPeriodId)
        .setSwarmNode(randomNode)
        .build();
    }

    Instant earliestValidAckRequestTimestamp = now.minus(
      swarmConfig.getMessageTimeout()
    );

    if (!swarmState.getLastAckRequest().isPresent()) {
      LOG.debug("No last ack");
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
    List<ProxyTarget> proxyTargetsList = getRandomNodes(
        failureSubgroup,
        Optional.of(lastAckRequest.getSwarmNode())
      )
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

  private Collection<SwarmNode> getRandomNodes(
    int number,
    Optional<SwarmNode> proxyFor
  ) {
    Set<SwarmNode> disallowedNodes = new HashSet<>();
    disallowedNodes.add(swarmConfig.getLocalNode());
    proxyFor.ifPresent(disallowedNodes::add);

    List<SwarmNode> allowedNodes = clusterNodesList
      .stream()
      .filter(node -> !disallowedNodes.contains(node))
      .collect(ImmutableList.toImmutableList());

    Preconditions.checkArgument(
      number > 0,
      "Must be greater than 0 (%s)",
      number
    );
    Preconditions.checkArgument(
      number <= allowedNodes.size(),
      "Cannot request more than %s random nodes (%s)",
      allowedNodes.size(),
      number
    );

    if (number == allowedNodes.size()) {
      return allowedNodes;
    }

    Set<SwarmNode> randomNodes = new HashSet<>(number);

    while (randomNodes.size() < number) {
      int randomIndex = ThreadLocalRandom
        .current()
        .nextInt(0, allowedNodes.size());

      randomNodes.add(allowedNodes.get(randomIndex));
    }

    return randomNodes;
  }

  private SwarmNode getRandomNode() {
    return Iterables.getOnlyElement(getRandomNodes(1, Optional.empty()));
  }

  PingResponse handle(PingMessage pingMessage) {
    if (
      pingMessage.getProxyFor().isPresent() &&
      pingMessage.getProxyFor().get().equals(pingMessage.getSender())
    ) {
      return PingProxy
        .builder()
        .setSwarmNode(pingMessage.getProxyFor().get())
        .build();
    }

    return com.github.nhirakawa.swarm.protocol.model.ping.PingAck.instance();
  }

  Result<PingAck, PingAckError> handle(PingAckMessage pingAckMessage) {
    if (pingAckMessage.getProxyFor().isPresent()) {
      if (
        pingAckMessage.getProxyFor().get().equals(swarmConfig.getLocalNode())
      ) {
        // do nothing
      } else {
        return Result.ok(
          AcknowledgeProxy
            .builder()
            .setProxyFor(pingAckMessage.getProxyFor().get())
            .build()
        );
      }
    }

    SwarmState currentSwarmState = swarmStateBuffer.getCurrent();

    Instant now = clock.instant();

    if (!currentSwarmState.getLastAckRequest().isPresent()) {
      LOG.warn("No outstanding ping request");
      return Result.err(PingAckError.NO_OUTSTANDING_PICK_REQUEST);
    }

    LastAckRequest lastAckRequest = currentSwarmState.getLastAckRequest().get();

    if (
      !pingAckMessage
        .getProtocolPeriodId()
        .equals(lastAckRequest.getProtocolPeriodId())
    ) {
      LOG.debug(
        "{} does not match current protocol period ID ({})",
        pingAckMessage.getProtocolPeriodId(),
        lastAckRequest.getProtocolPeriodId()
      );

      return Result.err(PingAckError.INVALID_PROTOCOL_ID);
    }

    if (!pingAckMessage.getSender().equals(lastAckRequest.getSwarmNode())) {
      LOG.debug(
        "{} does not match last ack request ({})",
        pingAckMessage.getSender(),
        lastAckRequest.getSwarmNode()
      );

      return Result.err(PingAckError.INVALID_SENDER);
    }

    LOG.debug("{} has acknowledged ping", pingAckMessage.getSender());

    SwarmState updatedSwarmState = SwarmState
      .builder()
      .from(currentSwarmState)
      .setTimestamp(now)
      .setLastAckRequest(Optional.empty())
      .setLastProtocolPeriodId(UUID.randomUUID().toString())
      .build();

    swarmStateBuffer.add(updatedSwarmState);

    return Result.ok(
      AcknowledgePing.builder().setTimestamp(clock.instant()).build()
    );
  }
}
