package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.util.Jitter;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO - Document this
public class WaitingForNextProtocolPeriodProtocolState
	extends SwarmProtocolState
{

	private static final Logger LOG = LogManager.getLogger(
		WaitingForNextProtocolPeriodProtocolState.class
	);

	private final Duration jitteredProtocolPeriod;

	WaitingForNextProtocolPeriodProtocolState(ProtocolStateContext context) {
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

		context()
			.memberRegistry()
			.promoteExpiredSuspicions(context().swarmConfig().getSuspicionTimeout());

		context()
			.memberRegistry()
			.evictConfirmedNodes(context().swarmConfig().getConfirmedRetention());

		Optional<SwarmAddress> pingTarget = context()
			.memberRegistry()
			.getPingTarget();

		Transition.Builder transition = Transition.builder();
		if (pingTarget.isPresent()) {
			List<MemberStatus> gossip = context().memberRegistry().getGossipPayload(3);
			transition
				.setNextSwarmProtocolState(
					new WaitingForAckProtocolState(context().next(), pingTarget.get())
				)
				.addResponsesToSend(
					new PingRequest(
						context().swarmConfig().getLocalAddress(),
						pingTarget.get(),
						Optional.empty(),
						context().protocolPeriodId(),
						gossip
					)
				);
		} else {
			LOG.debug("No member is eligible for ping");
			transition.setNextSwarmProtocolState(
				new WaitingForNextProtocolPeriodProtocolState(context().next())
			);
		}

		// With no one left to ping, the node is cut off, so it looks for peers every
		// period, as during join, instead of waiting for the rediscovery interval.
		Stopwatch rediscoveryStopwatch = context().rediscoveryStopwatch();
		if (
			pingTarget.isEmpty() ||
			rediscoveryStopwatch.elapsed().toNanos() >=
			context().swarmConfig().getRediscoveryInterval().toNanos()
		) {
			LOG.debug("Broadcasting rediscovery request");
			rediscoveryStopwatch.reset().start();
			transition.addResponsesToSend(
				new DiscoveryRequest(
					context().swarmConfig().getLocalAddress(),
					context().swarmConfig().getMulticastAddress()
				)
			);
		}

		return Optional.of(transition.build());
	}

	@Override
	public Optional<Transition> applyPingAck(PingAck pingAck) {
		LOG.debug("Ignoring {}", pingAck);
		return Optional.empty();
	}
}
