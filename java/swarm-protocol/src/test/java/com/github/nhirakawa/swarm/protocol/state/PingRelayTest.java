package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.fake.FakeTicker;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingRelayTest {

	private static final SwarmAddress PROXY = new InMemorySwarmAddress("proxy");
	private static final SwarmAddress REQUESTER = new InMemorySwarmAddress(
		"requester"
	);
	private static final SwarmAddress TARGET = new InMemorySwarmAddress(
		"target"
	);
	private static final long REQUESTER_PERIOD_ID = 42L;

	private static final SwarmConfig SWARM_CONFIG = SwarmConfig.builder()
		.setLocalAddress(PROXY)
		.setMulticastAddress(new InMemorySwarmAddress("MULTICAST"))
		.setFailureSubGroup(1)
		.setProtocolTick(Duration.ofMillis(100))
		.setMessageTimeout(Duration.ofMillis(200))
		.setProtocolPeriod(Duration.ofSeconds(1))
		.build();

	private FakeTicker ticker;
	private MemberRegistry registry;
	private PingRelays relays;
	private WaitingForNextProtocolPeriodProtocolState state;

	@BeforeEach
	void setup() {
		ticker = new FakeTicker();
		registry = new MemberRegistry(Set.of(REQUESTER, TARGET), ticker);
		relays = new PingRelays(ticker);
		state = new WaitingForNextProtocolPeriodProtocolState(
			new ProtocolStateContext(
				SWARM_CONFIG,
				4L,
				1L,
				Stopwatch.createStarted(ticker),
				registry,
				() -> {},
				new RefutationTracker(ticker),
				Stopwatch.createStarted(ticker),
				relays
			)
		);
	}

	@Test
	void itAcksAPlainPingWithThePingsId() {
		Transition transition = state
			.applyPing(
				new PingRequest(
					REQUESTER,
					PROXY,
					Optional.empty(),
					REQUESTER_PERIOD_ID,
					List.of()
				)
			)
			.orElseThrow();

		PingAck ack = (PingAck) transition.getResponsesToSend().get(0);
		assertThat(ack.target()).isEqualTo(REQUESTER);
		assertThat(ack.proxyFor()).isEmpty();
		assertThat(ack.protocolPeriodId()).isEqualTo(REQUESTER_PERIOD_ID);
	}

	@Test
	void itPingsTheTargetInsteadOfAckingAProxyRequest() {
		List<StateMachineMessage> responses = requestProbe();

		assertThat(responses).hasSize(1);
		PingRequest ping = (PingRequest) responses.get(0);
		assertThat(ping.source()).isEqualTo(PROXY);
		assertThat(ping.target()).isEqualTo(TARGET);
		assertThat(ping.proxyFor()).isEmpty();
		assertThat(relays.size()).isEqualTo(1);
	}

	@Test
	void itForwardsTheTargetsAckToTheRequester() {
		PingRequest ping = (PingRequest) requestProbe().get(0);
		List<MemberStatus> targetGossip = List.of(
			MemberStatus.alive(REQUESTER, 3L)
		);

		Transition transition = state
			.applyRelayAck(
				new PingAck(
					TARGET,
					PROXY,
					Optional.empty(),
					ping.protocolPeriodId(),
					7L,
					targetGossip
				)
			)
			.orElseThrow();

		assertThat(transition.getResponsesToSend()).containsExactly(
			new PingAck(
				PROXY,
				REQUESTER,
				Optional.of(TARGET),
				REQUESTER_PERIOD_ID,
				7L,
				targetGossip
			)
		);
		assertThat(transition.getNextSwarmProtocolState()).isSameAs(state);
		assertThat(registry.get(TARGET)).contains(MemberStatus.alive(TARGET, 7L));
		assertThat(relays.size()).isZero();
	}

	@Test
	void itIgnoresAcksThatDoNotAnswerARelay() {
		PingRequest ping = (PingRequest) requestProbe().get(0);

		assertThat(state.applyRelayAck(ackFrom(TARGET, ping.protocolPeriodId() + 1)))
			.isEmpty();
		assertThat(state.applyRelayAck(ackFrom(REQUESTER, ping.protocolPeriodId())))
			.isEmpty();
		assertThat(relays.size()).isEqualTo(1);
	}

	@Test
	void itForgetsRelaysAfterTheProtocolPeriod() {
		PingRequest ping = (PingRequest) requestProbe().get(0);
		ticker.write(SWARM_CONFIG.getProtocolPeriod().toNanos() + 1);

		assertThat(state.applyRelayAck(ackFrom(TARGET, ping.protocolPeriodId())))
			.isEmpty();
		assertThat(relays.size()).isZero();
	}

	@Test
	void itRelaysOnlyOnce() {
		PingRequest ping = (PingRequest) requestProbe().get(0);

		assertThat(state.applyRelayAck(ackFrom(TARGET, ping.protocolPeriodId())))
			.isPresent();
		assertThat(state.applyRelayAck(ackFrom(TARGET, ping.protocolPeriodId())))
			.isEmpty();
	}

	private List<StateMachineMessage> requestProbe() {
		return state
			.applyPing(
				new PingRequest(
					REQUESTER,
					PROXY,
					Optional.of(TARGET),
					REQUESTER_PERIOD_ID,
					List.of()
				)
			)
			.orElseThrow()
			.getResponsesToSend();
	}

	private static PingAck ackFrom(SwarmAddress source, long pingId) {
		return new PingAck(source, PROXY, Optional.empty(), pingId, 0L, List.of());
	}
}
