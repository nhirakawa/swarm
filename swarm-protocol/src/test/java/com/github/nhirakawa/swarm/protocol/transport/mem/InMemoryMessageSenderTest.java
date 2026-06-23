package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMessageSenderTest {

  private InMemoryTransportRegistry registry;
  private NetworkSimulator networkSimulator;
  private SwarmAddress senderAddress;
  private SwarmAddress receiverAddress;
  private InMemoryTransport receiverTransport;
  private InMemoryMessageSender sender;

  @BeforeEach
  void setUp() throws TimeoutException {
    NetworkSimulationConfig config = new PerfectNetworkSimulationConfig();
    registry = new InMemoryTransportRegistry();
    networkSimulator = new NetworkSimulator(registry, config);
    networkSimulator.startAsync().awaitRunning(Duration.ofSeconds(10));
    senderAddress = new InMemorySwarmAddress("sender");
    receiverAddress = new InMemorySwarmAddress("receiver");
    receiverTransport = new InMemoryTransport(receiverAddress, registry, ObjectMapperWrapper.instance().writer(), ObjectMapperWrapper.instance().reader(), networkSimulator);
    registry.register(receiverAddress, receiverTransport);
    sender = new InMemoryMessageSender(senderAddress, networkSimulator);
  }

  @AfterEach
  void tearDown() throws TimeoutException {
    registry.clear();
    networkSimulator.stopAsync().awaitTerminated(Duration.ofSeconds(10));
  }

  @Test
  void testSendPingRequest() {
    StateMachineMessage response = new PingRequest(
        senderAddress,
      receiverAddress,
      Optional.empty(),
      4L
    );

    sender.send(response, Duration.ofMillis(10));

    // Verify the message was received
    InMemoryMessageReceiver receiver = receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    assertThat(receivedMessage.get()).isInstanceOf(PingRequest.class);

    PingRequest pingRequest = (PingRequest) receivedMessage.get();
    assertThat(pingRequest.source()).isEqualTo(senderAddress);
    assertThat(pingRequest.proxyFor()).isEmpty();
    assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingRequestWithProxy() {
    SwarmAddress proxyAddress = new InMemorySwarmAddress("proxy");
    StateMachineMessage response = new PingRequest(
        senderAddress,
      receiverAddress,
      Optional.of(proxyAddress),
      4L
    );

    sender.send(response, Duration.ofMillis(10));

    InMemoryMessageReceiver receiver =
				receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    PingRequest pingRequest = (PingRequest) receivedMessage.get();
    assertThat(pingRequest.source()).isEqualTo(senderAddress);
    assertThat(pingRequest.proxyFor()).contains(proxyAddress);
    assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingAck() {
    StateMachineMessage response = new PingAck(
        senderAddress,
      receiverAddress,
      Optional.empty(),
      4L
    );

    sender.send(response, Duration.ofMillis(10));

    InMemoryMessageReceiver receiver = receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    assertThat(receivedMessage.get()).isInstanceOf(PingAck.class);

    PingAck pingAck = (PingAck) receivedMessage.get();
    assertThat(pingAck.source()).isEqualTo(senderAddress);
    assertThat(pingAck.proxyFor()).isEmpty();
    assertThat(pingAck.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingAckWithProxy() {
    SwarmAddress proxyAddress = new InMemorySwarmAddress("proxy");
    StateMachineMessage response = new PingAck(
        senderAddress,
      receiverAddress,
      Optional.of(proxyAddress),
      4L
    );

    sender.send(response, Duration.ofMillis(10));

    InMemoryMessageReceiver receiver =
				receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    PingAck pingAck = (PingAck) receivedMessage.get();
    assertThat(pingAck.source()).isEqualTo(senderAddress);
    assertThat(pingAck.proxyFor()).contains(proxyAddress);
    assertThat(pingAck.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendToNonExistentNode() {
    SwarmAddress nonExistentAddress = new InMemorySwarmAddress("nonexistent");
    StateMachineMessage response = new PingRequest(
        senderAddress,
      nonExistentAddress,
      Optional.empty(),
      4L
    );

    // Should not throw, just log warning
    sender.send(response, Duration.ofMillis(10));

    // Verify message was not delivered to receiver
    InMemoryMessageReceiver receiver = receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(10)
    );

    assertThat(receivedMessage).isEmpty();
  }
}
