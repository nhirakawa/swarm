package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTransportTest {

	private InMemoryTransportRegistry registry;
	private NetworkSimulator networkSimulator;
	private InMemoryTransport transport1;
	private InMemoryTransport transport2;
	private SwarmAddress address1;
	private SwarmAddress address2;

	@BeforeEach
	void setUp() throws TimeoutException {
		NetworkSimulationConfig config = new PerfectNetworkSimulationConfig();
		registry = new InMemoryTransportRegistry();
		networkSimulator = new NetworkSimulator(registry, config);
		networkSimulator.startAsync().awaitRunning(Duration.ofSeconds(1));
		address1 = new InMemorySwarmAddress("asdf");
		address2 = new InMemorySwarmAddress("fdsa");
		transport1 = new InMemoryTransport(
			address1,
			registry,
			ObjectMapperWrapper.instance().writer(),
			ObjectMapperWrapper.instance().reader(),
			networkSimulator
		);
		transport2 = new InMemoryTransport(
			address2,
			registry,
			ObjectMapperWrapper.instance().writer(),
			ObjectMapperWrapper.instance().reader(),
			networkSimulator
		);
	}

	@AfterEach
	void tearDown() throws TimeoutException {
		if (transport1.isRunning()) {
			transport1.stopAsync().awaitTerminated(Duration.ofSeconds(1));
		}
		if (transport2.isRunning()) {
			transport2.stopAsync().awaitTerminated(Duration.ofSeconds(1));
		}
		registry.clear();
		networkSimulator.stopAsync().awaitTerminated(Duration.ofSeconds(1));
	}

	@Test
	void testTransportLifecycle() throws Exception {
		assertThat(registry.size()).isEqualTo(0);

		transport1.startAsync().awaitRunning();

		assertThat(registry.size()).isEqualTo(1);
		assertThat(registry.lookup(address1)).isPresent();

		transport1.stopAsync().awaitTerminated();

		assertThat(registry.size()).isEqualTo(0);
		assertThat(registry.lookup(address1)).isEmpty();
	}

	@Test
	void testMultipleTransports() throws Exception {
		transport1.startAsync().awaitRunning();
		transport2.startAsync().awaitRunning();

		assertThat(registry.size()).isEqualTo(2);
		assertThat(registry.lookup(address1)).isPresent();
		assertThat(registry.lookup(address2)).isPresent();
	}

	@Test
	void testSendMessageBetweenNodes() throws Exception {
		transport1.startAsync().awaitRunning();
		transport2.startAsync().awaitRunning();

		// Node 1 sends a ping request to Node 2
		StateMachineMessage response = new PingRequest(
			address1,
			address2,
			Optional.empty(),
			4L,
			List.of()
		);

		transport1.sender().send(response, Duration.ofMillis(10));

		// Node 2 receives the message
		Optional<StateMachineMessage> receivedMessage = transport2
			.receiver()
			.receive(Duration.ofMillis(100));

		assertThat(receivedMessage).isPresent();
		assertThat(receivedMessage.get()).isInstanceOf(PingRequest.class);

		PingRequest pingRequest = (PingRequest) receivedMessage.get();
		assertThat(pingRequest.source()).isEqualTo(address1);
		assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
	}

	@Test
	void testBidirectionalCommunication() throws Exception {
		transport1.startAsync().awaitRunning();
		transport2.startAsync().awaitRunning();

		// Node 1 -> Node 2
		transport1
			.sender()
			.send(
				new PingRequest(address1, address2, Optional.empty(), 4L, List.of()),
				Duration.ofMillis(10)
			);

		// Node 2 -> Node 1
		transport2
			.sender()
			.send(
				new PingRequest(address2, address1, Optional.empty(), 4L, List.of()),
				Duration.ofMillis(10)
			);

		// Verify Node 2 received source Node 1
		Optional<StateMachineMessage> message1 = transport2
			.receiver()
			.receive(Duration.ofMillis(100));
		assertThat(message1)
			.isPresent()
			.hasValueSatisfying(innerMessage1 ->
				assertThat(innerMessage1.source()).isEqualTo(address1)
			);

		// Verify Node 1 received source Node 2
		Optional<StateMachineMessage> message2 = transport1
			.receiver()
			.receive(Duration.ofMillis(100));
		assertThat(message2)
			.isPresent()
			.hasValueSatisfying(innerMessage2 ->
				assertThat(innerMessage2.source()).isEqualTo(address2)
			);
	}

	@Test
	void testGetLocalAddress() {
		assertThat(transport1.getLocalAddress()).isEqualTo(address1);
		assertThat(transport2.getLocalAddress()).isEqualTo(address2);
	}

	@Test
	void testReceiverReturnsCorrectImplementation() {
		assertThat(transport1.receiver()).isInstanceOf(
			InMemoryMessageReceiver.class
		);
	}

	@Test
	void testSenderReturnsCorrectImplementation() {
		assertThat(transport1.sender()).isInstanceOf(InMemoryMessageSender.class);
	}
}
