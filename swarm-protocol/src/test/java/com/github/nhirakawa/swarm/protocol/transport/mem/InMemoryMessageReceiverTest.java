package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
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
  void testEnqueueAndReceive() {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    StateMachineMessage message = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-1"
    );

    boolean enqueued = receiver.enqueue(message);
    assertThat(enqueued).isTrue();

    Optional<StateMachineMessage> result = receiver.receive(
      Duration.ofMillis(100)
    );

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(message);
  }

  @Test
  void testEnqueueMultipleMessages() {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
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

    receiver.enqueue(message1);
    receiver.enqueue(message2);

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
  void testEnqueueWhenQueueFull() {
    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");

    // Fill the queue to capacity
    for (int i = 0; i < QUEUE_CAPACITY; i++) {
      StateMachineMessage message = new InboundPingRequest(
        from,
        Optional.empty(),
        "period-" + i
      );
      boolean enqueued = receiver.enqueue(message);
      assertThat(enqueued).isTrue();
    }

    // Try to enqueue one more message - should fail
    StateMachineMessage extraMessage = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-extra"
    );
    boolean enqueued = receiver.enqueue(extraMessage);

    assertThat(enqueued).isFalse();
    assertThat(receiver.queueSize()).isEqualTo(QUEUE_CAPACITY);
  }

  @Test
  void testQueueSize() {
    assertThat(receiver.queueSize()).isEqualTo(0);

    SwarmAddress from = new SwarmAddress("192.168.1.1", 8080, "node-1");
    StateMachineMessage message = new InboundPingRequest(
      from,
      Optional.empty(),
      "period-1"
    );

    receiver.enqueue(message);
    assertThat(receiver.queueSize()).isEqualTo(1);

    receiver.receive(Duration.ofMillis(100));
    assertThat(receiver.queueSize()).isEqualTo(0);
  }
}
