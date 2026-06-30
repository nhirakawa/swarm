package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO - Document this
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

		long knownIncarnation = context()
			.memberRegistry()
			.get(pingTarget)
			.map(MemberStatus::incarnation)
			.orElse(0L);

		context()
			.memberRegistry()
			.put(
				pingTarget,
				new MemberStatus.Suspected(pingTarget, knownIncarnation)
			);

		SwarmProtocolState nextSwarmProtocolState =
			new WaitingForNextProtocolPeriodProtocolState(context());

		// TODO - Mark `pingTarget` as suspected

		Transition transition = Transition.builder()
			.setNextSwarmProtocolState(nextSwarmProtocolState)
			.build();

		return Optional.of(transition);
	}

	@Override
	public Optional<Transition> applyPingAck(PingAck pingAck) {
		if (pingAck.proxyFor().isEmpty()) {
			LOG.warn("Expected proxy-for but did not find one - {}", pingAck);

			return Optional.empty();
		}

		if (!pingAck.proxyFor().get().equals(pingTarget)) {
			LOG.warn(
				"Expected proxy-for to be {} but was {} - {}",
				pingAck.proxyFor().get(),
				pingTarget,
				pingAck
			);

			return Optional.empty();
		}

		if (!proxyTargets.contains(pingAck.source())) {
			LOG.warn(
				"{} was not one of the expected proxy targets ({}) - {}",
				pingAck.source(),
				proxyTargets,
				pingAck
			);

			return Optional.empty();
		}

		context()
			.memberRegistry()
			.put(pingTarget, MemberStatus.alive(pingTarget, pingAck.incarnation()));

		for (MemberStatus memberStatus : pingAck.gossip()) {
			context().memberRegistry().put(memberStatus.address(), memberStatus);
		}

		List<StateMachineMessage> refutations = buildRefutationPings(
			pingAck.gossip()
		);
		SwarmProtocolState nextState =
			new WaitingForNextProtocolPeriodProtocolState(context());

		return Optional.of(
			Transition.builder()
				.setNextSwarmProtocolState(nextState)
				.addAllResponsesToSend(refutations)
				.build()
		);
	}
}
