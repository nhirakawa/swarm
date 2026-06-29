package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.util.Jitter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO - Document this
public class WaitingForNextProtocolPeriodProtocolState
  extends SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    WaitingForNextProtocolPeriodProtocolState.class
  );

  private final Duration jitteredProtocolPeriod;

  WaitingForNextProtocolPeriodProtocolState(
    ProtocolStateContext context
  ) {
    super(context);
    this.jitteredProtocolPeriod = Jitter.apply(
      context.swarmConfig().getProtocolPeriod(),
      context.swarmConfig().getProtocolPeriodJitter()
    );
  }

  @Override
  public Optional<Transition> applyTick() {
    if (context().elapsed().toNanos() < jitteredProtocolPeriod.toNanos()) {
      return Optional.empty();
    }

    LOG.debug("Current protocol period has ended");

    context().memberRegistry().promoteExpiredSuspicions(
        context().swarmConfig().getSuspicionTimeout()
    );

    SwarmAddress pingTarget = context().memberRegistry().getPingTarget();

    List<MemberStatus> gossip = context().memberRegistry().getGossipPayload(3);

    SwarmProtocolState newState = new WaitingForAckProtocolState(
        context().next(),
      pingTarget
    );

    StateMachineMessage pingRequest = new PingRequest(
        context().swarmConfig().getLocalAddress(),
      pingTarget,
      Optional.empty(),
      context().protocolPeriodId(),
      gossip
    );

    Transition transition = Transition
      .builder()
      .setNextSwarmProtocolState(newState)
      .addResponsesToSend(pingRequest)
      .build();

    return Optional.of(transition);
  }

  @Override
  public Optional<Transition> applyPingAck(PingAck pingAck) {
    LOG.debug("Ignoring {}", pingAck);
    return Optional.empty();
  }
}
