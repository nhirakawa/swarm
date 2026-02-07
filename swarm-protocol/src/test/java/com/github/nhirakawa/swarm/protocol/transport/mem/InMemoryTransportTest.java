package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequestResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;
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
    NetworkSimulationConfig config = DefaultNetworkSimulationConfig.perfect();
    registry = new InMemoryTransportRegistry();
    networkSimulator = new NetworkSimulator(registry, config);
    networkSimulator.startAsync().awaitRunning(Duration.ofSeconds(1));
    address1 = new SwarmAddress("192.168.1.1", 8080, "node-1");
    address2 = new SwarmAddress("192.168.1.2", 8080, "node-2");
    transport1 = new InMemoryTransport(address1, registry, networkSimulator);
    transport2 = new InMemoryTransport(address2, registry, networkSimulator);
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
    PingRequestResponse response = new PingRequestResponse(
      address2,
      Optional.empty(),
      4L
    );

    transport1.sender().send(response);

    // Node 2 receives the message
    Optional<StateMachineMessage> receivedMessage = transport2
      .receiver()
      .receive(Duration.ofMillis(100));

    assertThat(receivedMessage).isPresent();
    assertThat(receivedMessage.get()).isInstanceOf(InboundPingRequest.class);

    InboundPingRequest pingRequest = (InboundPingRequest) receivedMessage.get();
    assertThat(pingRequest.from()).isEqualTo(address1);
    assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testBidirectionalCommunication() throws Exception {
    transport1.startAsync().awaitRunning();
    transport2.startAsync().awaitRunning();

    // Node 1 -> Node 2
    transport1.sender().send(
        new PingRequestResponse(address2, Optional.empty(), 4L)
      );

    // Node 2 -> Node 1
    transport2.sender().send(
        new PingRequestResponse(address1, Optional.empty(), 4L)
      );

    // Verify Node 2 received from Node 1
    Optional<StateMachineMessage> message1 = transport2
      .receiver()
      .receive(Duration.ofMillis(100));
    assertThat(message1).isPresent();
    assertThat(((InboundPingRequest) message1.get()).from()).isEqualTo(
      address1
    );

    // Verify Node 1 received from Node 2
    Optional<StateMachineMessage> message2 = transport1
      .receiver()
      .receive(Duration.ofMillis(100));
    assertThat(message2).isPresent();
    assertThat(((InboundPingRequest) message2.get()).from()).isEqualTo(
      address2
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
