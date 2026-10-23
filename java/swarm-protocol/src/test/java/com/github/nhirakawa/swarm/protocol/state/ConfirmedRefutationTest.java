package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.fake.FakeTicker;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmedRefutationTest {

	private static final SwarmAddress LOCAL = new InMemorySwarmAddress("host-1");
	private static final SwarmAddress PEER = new InMemorySwarmAddress("host-2");
	private static final SwarmAddress MULTICAST = new InMemorySwarmAddress(
		"MULTICAST"
	);

	private static final SwarmConfig SWARM_CONFIG = SwarmConfig.builder()
		.setLocalAddress(LOCAL)
		.setMulticastAddress(MULTICAST)
		.setProtocolPeriod(Duration.ofSeconds(1))
		.setProtocolTick(Duration.ofMillis(100))
		.setMessageTimeout(Duration.ofMillis(200))
		.setFailureSubGroup(1)
		.setMaxRefutations(1)
		.setRefutationWindow(Duration.ofSeconds(60))
		.setRediscoveryInterval(Duration.ofSeconds(10))
		.build();

	private FakeTicker ticker;
	private MemberRegistry registry;
	private AtomicInteger terminations;
	private ProtocolStateContext context;
	private WaitingForNextProtocolPeriodProtocolState state;

	@BeforeEach
	void setup() {
		ticker = new FakeTicker();
		registry = new MemberRegistry(Set.of(PEER), ticker);
		terminations = new AtomicInteger();
		context = new ProtocolStateContext(
			SWARM_CONFIG,
			4L,
			1L,
			Stopwatch.createStarted(ticker),
			registry,
			terminations::incrementAndGet,
			new RefutationTracker(ticker),
			Stopwatch.createStarted(ticker)
		);
		state = new WaitingForNextProtocolPeriodProtocolState(context);
	}

	@Test
	void itRefutesConfirmationAboveTheAccusedIncarnation() {
		Optional<Transition> transition = state.applyDiscoveryResponse(
			confirmedAt(4L)
		);

		assertThat(context.incarnation()).isEqualTo(5L);
		assertThat(transition).isPresent();
		List<StateMachineMessage> pings = transition.get().getResponsesToSend();
		assertThat(pings).hasSize(1);
		PingRequest ping = (PingRequest) pings.get(0);
		assertThat(ping.target()).isEqualTo(PEER);
		assertThat(ping.gossip()).contains(MemberStatus.alive(LOCAL, 5L));
		assertThat(registry.get(LOCAL)).isEmpty();
		assertThat(terminations).hasValue(0);
	}

	@Test
	void itIgnoresStaleConfirmation() {
		Optional<Transition> transition = state.applyDiscoveryResponse(
			confirmedAt(0L)
		);

		assertThat(transition).isEmpty();
		assertThat(context.incarnation()).isEqualTo(1L);
	}

	@Test
	void itTerminatesAfterTooManyRefutationsInWindow() {
		state.applyDiscoveryResponse(confirmedAt(context.incarnation()));
		Optional<Transition> transition = state.applyDiscoveryResponse(
			confirmedAt(context.incarnation())
		);

		assertThat(terminations).hasValue(1);
		assertThat(transition).isEmpty();
	}

	@Test
	void itForgetsRefutationsOutsideWindow() {
		state.applyDiscoveryResponse(confirmedAt(context.incarnation()));
		ticker.write(SWARM_CONFIG.getRefutationWindow().toNanos() + 1);
		state.applyDiscoveryResponse(confirmedAt(context.incarnation()));

		assertThat(terminations).hasValue(0);
	}

	@Test
	void itBroadcastsRediscoveryOnceIntervalElapses() {
		ticker.write(SWARM_CONFIG.getRediscoveryInterval().toNanos());

		Optional<Transition> transition = state.applyTick();

		assertThat(transition).isPresent();
		assertThat(transition.get().getResponsesToSend()).contains(
			new DiscoveryRequest(LOCAL, MULTICAST)
		);
	}

	@Test
	void itDoesNotBroadcastRediscoveryBeforeInterval() {
		ticker.write(SWARM_CONFIG.getProtocolPeriod().toNanos() * 2);

		Optional<Transition> transition = state.applyTick();

		assertThat(transition).isPresent();
		assertThat(transition.get().getResponsesToSend())
			.noneMatch(DiscoveryRequest.class::isInstance);
	}

	private static DiscoveryResponse confirmedAt(long incarnation) {
		return new DiscoveryResponse(
			PEER,
			LOCAL,
			List.of(
				MemberStatus.alive(PEER, 0L),
				MemberStatus.confirmed(LOCAL, incarnation)
			)
		);
	}
}
