package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.github.nhirakawa.swarm.protocol.ObjectMapperWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMessageReceiverTest {

  private InMemoryMessageReceiver receiver;
  private static final int QUEUE_CAPACITY = 10;

  @BeforeEach
  void setUp() {
    receiver = new InMemoryMessageReceiver(
        QUEUE_CAPACITY,
        ObjectMapperWrapper.instance().reader()
    );
  }

  @Test
  void testReceiveWithNoMessages() {
    Optional<StateMachineMessage> result = receiver.receive(
      Duration.ofMillis(10)
    );

    assertThat(result).isEmpty();
  }

  @Test
  void testEnqueueAndReceive() throws Exception {
    SwarmAddress from = new InMemorySwarmAddress("node-1");
    SwarmAddress to = new InMemorySwarmAddress("node-2");
    StateMachineMessage message = new PingRequest(
      from,
      to,
      Optional.empty(),
      4L
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
  void testEnqueueMultipleMessages() throws Exception {
    SwarmAddress from = new InMemorySwarmAddress("node-1");
    SwarmAddress to = new InMemorySwarmAddress("node-2");
    StateMachineMessage message1 = new PingRequest(
      from,
      to,
      Optional.empty(),
      4L
    );
    StateMachineMessage message2 = new PingRequest(
      from,
      to,
      Optional.empty(),
      4L
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

    assertThat(result1).contains(message1);
    assertThat(result2).contains(message2);
    assertThat(receiver.queueSize()).isEqualTo(0);
  }

  @Test
  void testEnqueueWhenQueueFull() throws Exception {
    SwarmAddress from = new InMemorySwarmAddress("node-1");
    SwarmAddress to = new InMemorySwarmAddress("node-2");

    // Fill the queue to capacity
    for (int i = 0; i < QUEUE_CAPACITY; i++) {
      StateMachineMessage message = new PingRequest(
        from,
        to,
        Optional.empty(),
        4 + i
      );
      WireMessage wireMessage = createWireMessage(from, to, message);
      boolean enqueued = receiver.enqueue(wireMessage, Duration.ofMillis(100));
      assertThat(enqueued).isTrue();
    }

    // Try to enqueue one more message - should fail after timeout
    StateMachineMessage extraMessage = new PingRequest(
      from,
      to,
      Optional.empty(),
      4L
    );
    WireMessage extraWireMessage = createWireMessage(from, to, extraMessage);
    boolean enqueued = receiver.enqueue(extraWireMessage, Duration.ofMillis(10));

    assertThat(enqueued).isFalse();
    assertThat(receiver.queueSize()).isEqualTo(QUEUE_CAPACITY);
  }

  @Test
  void testQueueSize() throws Exception {
    assertThat(receiver.queueSize()).isEqualTo(0);

    SwarmAddress from = new InMemorySwarmAddress("node-1");
    SwarmAddress to = new InMemorySwarmAddress("node-2");
    StateMachineMessage message = new PingRequest(
      from,
      to,
      Optional.empty(),
      4L
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
  ) throws Exception {
    ObjectMapper objectMapper = ObjectMapperWrapper.instance();
    byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);

    MessageHeader header = new MessageHeader.Builder()
        .messageVersion(MessageVersion.V0)
        .type(SwarmMessageType.PING_REQUEST)
        .compression(Compression.NONE)
        .serialization(Serialization.JSON)
        .payloadLength(payloadBytes.length)
        .messageId(1L)
        .timestamp(System.currentTimeMillis())
        .checksum(0L)
        .build();

    return new WireMessage(source, target, header, payloadBytes);
  }
}
