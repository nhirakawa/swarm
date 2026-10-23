package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.util.ArrayList;
import java.util.Comparator;
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
		SwarmAddress self = context.swarmConfig().getLocalAddress();

		if (!pingRequest.source().equals(self)) {
			context
				.memberRegistry()
				.put(pingRequest.source(), MemberStatus.alive(pingRequest.source(), 0));
		}

		mergeMemberStatuses(pingRequest.gossip());

		List<StateMachineMessage> refutations = buildRefutationPings(
			pingRequest.gossip()
		);
		List<MemberStatus> gossip = context.memberRegistry().getGossipPayload(3);

		return Optional.of(
			Transition.builder()
				.setNextSwarmProtocolState(this)
				.addResponsesToSend(
					new PingAck(
						context.swarmConfig().getLocalAddress(),
						pingRequest.source(),
						Optional.empty(),
						ThreadLocalRandom.current().nextLong(),
						context.incarnation(),
						gossip
					)
				)
				.addAllResponsesToSend(refutations)
				.build()
		);
	}

	/**
	 * Merges member statuses learned from a peer into the registry. Statuses about
	 * this node are skipped: those are refuted, never stored.
	 */
	final void mergeMemberStatuses(List<MemberStatus> memberStatuses) {
		SwarmAddress self = context.swarmConfig().getLocalAddress();
		for (MemberStatus memberStatus : memberStatuses) {
			if (!memberStatus.address().equals(self)) {
				context.memberRegistry().put(memberStatus.address(), memberStatus);
			}
		}
	}

	/**
	 * If peers suspect or have confirmed this node at its current incarnation or
	 * later, raises the incarnation past theirs and builds pings that gossip the
	 * new ALIVE status.
	 * <p>
	 * Refuting a confirmation counts toward the flapping limit; past it, the node
	 * terminates instead of refuting.
	 */
	final List<StateMachineMessage> buildRefutationPings(
		List<MemberStatus> gossip
	) {
		var self = context.swarmConfig().getLocalAddress();
		Optional<MemberStatus> accusation = gossip
			.stream()
			.filter(s -> s.address().equals(self))
			.filter(s -> !(s instanceof MemberStatus.Alive))
			.filter(s -> s.incarnation() >= context.incarnation())
			.max(Comparator.comparingLong(MemberStatus::incarnation));
		if (accusation.isEmpty()) {
			return List.of();
		}

		if (
			accusation.get() instanceof MemberStatus.Confirmed &&
			context
				.refutationTracker()
				.recordAndCheckExceeded(
					context.swarmConfig().getMaxRefutations(),
					context.swarmConfig().getRefutationWindow()
				)
		) {
			LOG.warn(
				"Local node {} was confirmed dead more than {} times within {} — terminating",
				self,
				context.swarmConfig().getMaxRefutations(),
				context.swarmConfig().getRefutationWindow()
			);
			context.terminationCallback().onRepeatedlyConfirmed();
			return List.of();
		}

		context.refuteAbove(accusation.get().incarnation());
		long newIncarnation = context.incarnation();
		LOG.info(
			"Refuting {} for local node {} at incarnation {}",
			accusation.get().type(),
			self,
			newIncarnation
		);

		List<MemberStatus> refuteGossip = new ArrayList<>();
		refuteGossip.add(MemberStatus.alive(self, newIncarnation));
		refuteGossip.addAll(context.memberRegistry().getGossipPayload(2));

		Set<SwarmAddress> targets = context
			.memberRegistry()
			.getFailureSubGroup(context.swarmConfig().getFailureSubGroup(), self);

		return targets
			.stream()
			.map(
				target ->
					(StateMachineMessage) new PingRequest(
						self,
						target,
						Optional.empty(),
						context.protocolPeriodId(),
						refuteGossip
					)
			)
			.toList();
	}

	Optional<Transition> applyPingAck(PingAck pingAck) {
		return Optional.empty();
	}

	Optional<Transition> applyDiscoveryRequest(DiscoveryRequest request) {
		LOG.debug("Received discovery request source {}", request.source());

		// TODO return a response less than 100% of the time
		// This would help limit the number of messages on the network if several new
		// members started in a short period of time

		// Always include our own status so that bootstrapping source nothing works -
		// even if the registry is empty, the requester learns about us.
		MemberStatus self = MemberStatus.alive(
			context.swarmConfig().getLocalAddress(),
			context.incarnation()
		);
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

		LOG.debug(
			"Sending discovery response to {} with {} members",
			request.source(),
			memberList.size()
		);

		return Optional.of(
			Transition.builder()
				.setNextSwarmProtocolState(this)
				.addResponsesToSend(response)
				.build()
		);
	}

	Optional<Transition> applyDiscoveryResponse(DiscoveryResponse response) {
		LOG.debug(
			"Merging discovery response from {} with {} members",
			response.source(),
			response.memberList().size()
		);

		mergeMemberStatuses(response.memberList());

		List<StateMachineMessage> refutations = buildRefutationPings(
			response.memberList()
		);
		if (refutations.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(
			Transition.builder()
				.setNextSwarmProtocolState(this)
				.addAllResponsesToSend(refutations)
				.build()
		);
	}
}
