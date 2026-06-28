package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// todo(nhirakawa) document this
public class WaitingForPingProxyProtocolState extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    WaitingForPingProxyProtocolState.class
  );

  private final SwarmAddress pingTarget;
  private final Set<SwarmAddress> proxyTargets;

  WaitingForPingProxyProtocolState(
    ProtocolStateContext context,
    SwarmAddress pingTarget,
    Set<SwarmAddress> proxyTargets
  ) {
    super(context);
    this.pingTarget = pingTarget;
    this.proxyTargets = ImmutableSet.copyOf(proxyTargets);
  }

  @Override
  public Optional<Transition> applyTick() {
    if (
      context().elapsed().toNanos() <
      context().swarmConfig().getProtocolPeriod().toNanos()
    ) {
      return Optional.empty();
    }

    long knownIncarnation = context().memberRegistry()
        .get(pingTarget)
        .map(MemberStatus::incarnation)
        .orElse(0L);

    context().memberRegistry().put(
        pingTarget,
        new MemberStatus.Suspected(pingTarget, knownIncarnation)
    );

    SwarmProtocolState nextSwarmProtocolState = new WaitingForNextProtocolPeriodProtocolState(
      context()
    );

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(nextSwarmProtocolState)
      //      .setMemberStatusUpdate(
      //        MemberStatusUpdate
      //          .builder()
      //          .setIncarnationNumber(-1)
      //          .setNewMemberStatus(MemberStatus.SUSPECTED)
      //          .setSwarmNode(pingTarget)
      //          .build()
      //      )
      .build();

    return Optional.of(transition);
  }

  @Override
  public Optional<Transition> applyPingAck(PingAck pingAckMessage) {
    if (pingAckMessage.proxyFor().isEmpty()) {
      LOG.warn("Expected proxy-for but did not find one - {}", pingAckMessage);

      return Optional.empty();
    }

    if (!pingAckMessage.proxyFor().get().equals(pingTarget)) {
      LOG.warn(
        "Expected proxy-for to be {} but was {} - {}",
        pingAckMessage.proxyFor().get(),
        pingTarget,
        pingAckMessage
      );

      return Optional.empty();
    }

    if (!proxyTargets.contains(pingAckMessage.source())) {
      LOG.warn(
        "{} was not one of the expected proxy targets ({}) - {}",
        pingAckMessage.source(),
        proxyTargets,
        pingAckMessage
      );

      return Optional.empty();
    }

    context().memberRegistry().put(
        pingTarget,
        MemberStatus.alive(pingTarget, pingAckMessage.incarnation())
    );

    SwarmProtocolState nextState = new WaitingForNextProtocolPeriodProtocolState(
      context()
    );

    return Optional.of(
      Transition
        .builder()
        .setNextSwarmProtocolState(nextState)
        //        .setMemberStatusUpdate(
        //          MemberStatusUpdate
        //            .builder()
        //            .setNewMemberStatus(MemberStatus.ALIVE)
        //            .setSwarmNode(pingTarget)
        //            .setIncarnationNumber(1L)
        //            .build()
        //        )
        .build()
    );
  }
}
