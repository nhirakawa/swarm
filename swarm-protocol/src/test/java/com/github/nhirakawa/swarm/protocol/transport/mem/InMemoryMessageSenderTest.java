package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequestResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;

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
  void setUp() throws UnknownHostException {
    NetworkSimulationConfig config = DefaultNetworkSimulationConfig.perfect();
    registry = new InMemoryTransportRegistry();
    networkSimulator = new NetworkSimulator(registry, config);
    networkSimulator.startAsync().awaitRunning();
    senderAddress = new SwarmAddress("192.168.1.1", 8080, "sender");
    receiverAddress = new SwarmAddress("192.168.1.2", 8080, "receiver");
    receiverTransport = new InMemoryTransport(receiverAddress, registry, networkSimulator);
    registry.register(receiverAddress, receiverTransport);
    sender = new InMemoryMessageSender(senderAddress, networkSimulator);
  }

  @AfterEach
  void tearDown() {
    registry.clear();
    networkSimulator.stopAsync().awaitTerminated();
  }

  @Test
  void testSendPingRequest() {
    PingRequestResponse response = new PingRequestResponse(
      receiverAddress,
      Optional.empty(),
      4L
    );

    sender.send(response);

    // Verify the message was received
    InMemoryMessageReceiver receiver =
      (InMemoryMessageReceiver) receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    assertThat(receivedMessage.get()).isInstanceOf(InboundPingRequest.class);

    InboundPingRequest pingRequest = (InboundPingRequest) receivedMessage.get();
    assertThat(pingRequest.from()).isEqualTo(senderAddress);
    assertThat(pingRequest.onBehalfOf()).isEmpty();
    assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingRequestWithProxy() {
    SwarmAddress proxyAddress = new SwarmAddress(
      "192.168.1.3",
      8080,
      "proxy"
    );
    PingRequestResponse response = new PingRequestResponse(
      receiverAddress,
      Optional.of(proxyAddress),
      4L
    );

    sender.send(response);

    InMemoryMessageReceiver receiver =
      (InMemoryMessageReceiver) receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    InboundPingRequest pingRequest = (InboundPingRequest) receivedMessage.get();
    assertThat(pingRequest.from()).isEqualTo(senderAddress);
    assertThat(pingRequest.onBehalfOf()).contains(proxyAddress);
    assertThat(pingRequest.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingAck() {
    PingAckResponse response = new PingAckResponse(
      receiverAddress,
      Optional.empty(),
      4L
    );

    sender.send(response);

    InMemoryMessageReceiver receiver =
      (InMemoryMessageReceiver) receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    assertThat(receivedMessage.get()).isInstanceOf(InboundPingAck.class);

    InboundPingAck pingAck = (InboundPingAck) receivedMessage.get();
    assertThat(pingAck.from()).isEqualTo(senderAddress);
    assertThat(pingAck.proxyFor()).isEmpty();
    assertThat(pingAck.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendPingAckWithProxy() {
    SwarmAddress proxyAddress = new SwarmAddress(
      "192.168.1.3",
      8080,
      "proxy"
    );
    PingAckResponse response = new PingAckResponse(
      receiverAddress,
      Optional.of(proxyAddress),
      4L
    );

    sender.send(response);

    InMemoryMessageReceiver receiver =
      (InMemoryMessageReceiver) receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(receivedMessage).isPresent();
    InboundPingAck pingAck = (InboundPingAck) receivedMessage.get();
    assertThat(pingAck.from()).isEqualTo(senderAddress);
    assertThat(pingAck.proxyFor()).contains(proxyAddress);
    assertThat(pingAck.protocolPeriodId()).isEqualTo(4L);
  }

  @Test
  void testSendToNonExistentNode() {
    SwarmAddress nonExistentAddress = new SwarmAddress(
      "192.168.1.99",
      8080,
      "nonexistent"
    );
    PingRequestResponse response = new PingRequestResponse(
      nonExistentAddress,
      Optional.empty(),
      4L
    );

    // Should not throw, just log warning
    sender.send(response);

    // Verify message was not delivered to receiver
    InMemoryMessageReceiver receiver =
      (InMemoryMessageReceiver) receiverTransport.receiver();
    Optional<StateMachineMessage> receivedMessage = receiver.receive(
      Duration.ofMillis(10)
    );

    assertThat(receivedMessage).isEmpty();
  }
}
