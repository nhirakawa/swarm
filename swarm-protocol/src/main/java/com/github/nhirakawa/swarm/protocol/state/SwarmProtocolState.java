package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.protocol.Transition;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class SwarmProtocolState {
  protected final Instant protocolStartTimestamp;
  protected final SwarmConfig swarmConfig;
  protected final String protocolPeriodId;

  protected final List<SwarmNode> clusterNodesList;

  protected SwarmProtocolState(
    Instant protocolStartTimestamp,
    SwarmConfig swarmConfig,
    String protocolPeriodId
  ) {
    this.protocolStartTimestamp = protocolStartTimestamp;
    this.swarmConfig = swarmConfig;
    this.protocolPeriodId = protocolPeriodId;

    this.clusterNodesList = ImmutableList.copyOf(swarmConfig.getClusterNodes());
  }

  public String getProtocolPeriodId() {
    return protocolPeriodId;
  }

  public abstract Optional<Transition> applyTick(
    SwarmTimeoutMessage swarmTimeoutMessage
  );

  public abstract Optional<Transition> applyPingAck(
    PingAckMessage pingAckMessage
  );

  public static SwarmProtocolState initial(
    Instant timestamp,
    SwarmConfig swarmConfig,
    String protocolPeriodId
  ) {
    return new WaitingForNextProtocolPeriodProtocolState(
      timestamp,
      swarmConfig,
      protocolPeriodId
    );
  }

  protected Set<SwarmNode> getRandomNodes(
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
      return ImmutableSet.copyOf(allowedNodes);
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
}
