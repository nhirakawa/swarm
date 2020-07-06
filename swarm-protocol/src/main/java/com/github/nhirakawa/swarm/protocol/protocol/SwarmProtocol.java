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
import com.hubspot.algebra.Result;
import com.typesafe.config.Config;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

      return TimeoutResponse.builder().setTargetNode(randomNode).build();
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

    // TODO return a proxy message here

    return TimeoutResponses.empty();
  }

  PingAckMessage handle(PingMessage ignored) {
    return PingAckMessage.builder().setSender(localSwarmNode).build();
  }

  Result<PingAckResponse, ProtocolError> handle(PingAckMessage pingAckMessage) {
    SwarmState currentSwarmState = swarmStateBuffer.getCurrent();

    Instant now = clock.instant();

    if (!currentSwarmState.getLastAckRequest().isPresent()) {
      LOG.info("No outstanding ping request");
      return Result.err(ProtocolError.NO_OUTSTANDING_PING_REQUEST);
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
      return Result.err(ProtocolError.INVALID_PROTOCOL_PERIOD);
    }

    if (!pingAckMessage.getSender().equals(lastAckRequest.getSwarmNode())) {
      LOG.info(
        "{} does not match last ack request ({})",
        pingAckMessage.getSender(),
        lastAckRequest.getSwarmNode()
      );
      return Result.err(ProtocolError.INVALID_SENDER);
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

    return Result.ok(
      PingAckResponse.builder().setTimestamp(clock.instant()).build()
    );
  }

  public PingAckResponse handle(PingProxyRequest pingProxyRequest) {
    throw new UnsupportedOperationException();
  }
}
