package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Serialization;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMessageReceiverTest {

  private InMemoryMessageReceiver receiver;
  private static final int QUEUE_CAPACITY = 10;

  @BeforeEach
  void setUp() {
    receiver = new InMemoryMessageReceiver(QUEUE_CAPACITY);
  }

  @Test
  void testReceiveWithNoMessages() {
    Optional<StateMachineMessage> result = receiver.receive(
      Duration.ofMillis(10)
    );

    assertThat(result).isEmpty();
  }

  @Test
  void testEnqueueAndReceive() throws InterruptedException {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    SwarmAddress to = new SwarmAddress("192.168.1.2", 8080, "node-2");
    StateMachineMessage message = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-1"
    );

    WireMessage wireMessage = createWireMessage(from, to, message);
    boolean enqueued = receiver.enqueue(wireMessage, Duration.ofMillis(100));
    assertThat(enqueued).isTrue();

    Optional<StateMachineMessage> result = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(message);
  }

  @Test
  void testEnqueueMultipleMessages() throws InterruptedException {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    SwarmAddress to = new SwarmAddress("192.168.1.2", 8080, "node-2");
    StateMachineMessage message1 = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-1"
    );
    StateMachineMessage message2 = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-2"
    );

    WireMessage wireMessage1 = createWireMessage(from, to, message1);
    WireMessage wireMessage2 = createWireMessage(from, to, message2);
    receiver.enqueue(wireMessage1, Duration.ofMillis(100));
    receiver.enqueue(wireMessage2, Duration.ofMillis(100));

    assertThat(receiver.queueSize()).isEqualTo(2);

    Optional<StateMachineMessage> result1 = receiver.receive(
      Duration.ofMillis(100)
    );
    Optional<StateMachineMessage> result2 = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(result1.get()).isEqualTo(message1);
    assertThat(result2.get()).isEqualTo(message2);
    assertThat(receiver.queueSize()).isEqualTo(0);
  }

  @Test
  void testEnqueueWhenQueueFull() throws InterruptedException {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    SwarmAddress to = new SwarmAddress("192.168.1.2", 8080, "node-2");

    // Fill the queue to capacity
    for (int i = 0; i < QUEUE_CAPACITY; i++) {
      StateMachineMessage message = new InboundPingRequest(
        from,
        Optional.empty(),
        "period-" + i
      );
      WireMessage wireMessage = createWireMessage(from, to, message);
      boolean enqueued = receiver.enqueue(wireMessage, Duration.ofMillis(100));
      assertThat(enqueued).isTrue();
    }

    // Try to enqueue one more message - should fail after timeout
    StateMachineMessage extraMessage = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-extra"
    );
    WireMessage extraWireMessage = createWireMessage(from, to, extraMessage);
    boolean enqueued = receiver.enqueue(extraWireMessage, Duration.ofMillis(10));

    assertThat(enqueued).isFalse();
    assertThat(receiver.queueSize()).isEqualTo(QUEUE_CAPACITY);
  }

  @Test
  void testQueueSize() throws InterruptedException {
    assertThat(receiver.queueSize()).isEqualTo(0);

    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    SwarmAddress to = new SwarmAddress("192.168.1.2", 8080, "node-2");
    StateMachineMessage message = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-1"
    );

    WireMessage wireMessage = createWireMessage(from, to, message);
    receiver.enqueue(wireMessage, Duration.ofMillis(100));
    assertThat(receiver.queueSize()).isEqualTo(1);

    receiver.receive(Duration.ofMillis(100));
    assertThat(receiver.queueSize()).isEqualTo(0);
  }

  private WireMessage createWireMessage(
    SwarmAddress source,
    SwarmAddress target,
    StateMachineMessage payload
  ) {
    try {
      byte[] sourceIp = java.net.InetAddress.getByName(source.address()).getAddress();
      byte[] targetIp = java.net.InetAddress.getByName(target.address()).getAddress();

      MessageHeader header = new MessageHeader(
        MessageVersion.V0,
        SwarmMessageType.PING_REQUEST,
        Compression.NONE,
        Serialization.JSON,
        0,                // payload length placeholder
        1L,               // message ID placeholder
        System.currentTimeMillis(), // timestamp
        sourceIp,
        source.port(),
        targetIp,
        target.port(),
        0L                // checksum placeholder
      );
      return new WireMessage(source, target, header, payload);
    } catch (java.net.UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }
}
