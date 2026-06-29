package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class SwarmProtocolState {

  private static final Logger LOG = LogManager.getLogger(
    SwarmProtocolState.class
  );

  private final ProtocolStateContext context;

  SwarmProtocolState(ProtocolStateContext context) {
    this.context = context;
  }

  static SwarmProtocolState initial(ProtocolStateContext context) {
    if (context.swarmConfig().isDiscoveryEnabled()) {
      return new InitializingProtocolState(context);
    } else {
      return new WaitingForNextProtocolPeriodProtocolState(context);
    }
  }

  final ProtocolStateContext context() {
    return context;
  }

  final List<MemberStatus> getMemberStatuses() {
    return context.memberRegistry().getMemberStatuses();
  }

  abstract Optional<Transition> applyTick();

  Optional<Transition> applyPing(PingRequest pingRequest) {
    context
        .memberRegistry()
        .put(
            pingRequest.source(),
            MemberStatus.alive(pingRequest.source(), 0)
        );

    for (MemberStatus memberStatus : pingRequest.gossip()) {
      context.memberRegistry().put(memberStatus.address(), memberStatus);
    }

    List<StateMachineMessage> refutations = buildRefutationPings(pingRequest.gossip());
    List<MemberStatus> gossip = context.memberRegistry().getGossipPayload(3);

    return Optional.of(
        Transition
            .builder()
            .setNextSwarmProtocolState(this)
            .addResponsesToSend(new PingAck(context.swarmConfig().getLocalAddress(), pingRequest.source(), Optional.empty(), ThreadLocalRandom.current().nextLong(), context.incarnation(), gossip))
            .addAllResponsesToSend(refutations)
            .build()
    );
  }

  final List<StateMachineMessage> buildRefutationPings(List<MemberStatus> gossip) {
    var self = context.swarmConfig().getLocalAddress();
    boolean suspected = gossip.stream().anyMatch(s ->
        s instanceof MemberStatus.Suspected && s.address().equals(self)
            && s.incarnation() >= context.incarnation()
    );
    if (!suspected) {
      return List.of();
    }

    context.incrementIncarnation();
    long newIncarnation = context.incarnation();

    List<MemberStatus> refuteGossip = new ArrayList<>();
    refuteGossip.add(MemberStatus.alive(self, newIncarnation));
    refuteGossip.addAll(context.memberRegistry().getGossipPayload(2));

    Set<SwarmAddress> targets = context.memberRegistry().getFailureSubGroup(
        context.swarmConfig().getFailureSubGroup(), self
    );

    return targets.stream()
        .map(target -> (StateMachineMessage) new PingRequest(
            self, target, Optional.empty(),
            context.protocolPeriodId(), refuteGossip
        ))
        .toList();
  }

  Optional<Transition> applyPingAck(PingAck pingAck) {
    return Optional.empty();
  }

  Optional<Transition> applyDiscoveryRequest(
    DiscoveryRequest request
  ) {
    LOG.debug("Received discovery request source {}", request.source());

    // TODO return a response less than 100% of the time
    // This would help limit the number of messages on the network if several new
    // members started in a short period of time

    // Always include our own status so that bootstrapping source nothing works -
    // even if the registry is empty, the requester learns about us.
    MemberStatus self = MemberStatus.alive(context.swarmConfig().getLocalAddress(), context.incarnation());
    List<MemberStatus> gossip = context.memberRegistry().getGossipPayload(10);

    List<MemberStatus> memberList = new ArrayList<>(gossip.size() + 1);
    memberList.add(self);
    for (MemberStatus m : gossip) {
      if (!m.address().equals(context.swarmConfig().getLocalAddress())) {
        memberList.add(m);
      }
    }

    DiscoveryResponse response = new DiscoveryResponse(
        context.swarmConfig().getLocalAddress(),
        request.source(),
        memberList
    );

    LOG.debug("Sending discovery response to {} with {} members", request.source(), memberList.size());

    return Optional.of(
      Transition.builder()
        .setNextSwarmProtocolState(this)
        .addResponsesToSend(response)
        .build()
    );
  }

  Optional<Transition> applyDiscoveryResponse(
    DiscoveryResponse response
  ) {
    // Default: ignore discovery responses when not initializing
    LOG.debug(
      "Ignoring discovery response source {} - not initializing",
      response.source()
    );
    return Optional.empty();
  }
}
