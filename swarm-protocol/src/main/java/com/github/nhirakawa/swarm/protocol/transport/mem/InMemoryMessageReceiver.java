package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.ThreadSafe;

import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of SwarmMessageReceiver.
 * Maintains a blocking queue for inbound messages.
 */
@ThreadSafe
public class InMemoryMessageReceiver implements SwarmMessageReceiver {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryMessageReceiver.class
  );

  private final BlockingQueue<StateMachineMessage> inboundQueue;

  public InMemoryMessageReceiver(int queueCapacity) {
    this.inboundQueue = new LinkedBlockingQueue<>(queueCapacity);
  }

  @Override
  public Optional<StateMachineMessage> receive(Duration timeout) {
    try {
      StateMachineMessage message = inboundQueue.poll(
        timeout.toNanos(),
        TimeUnit.NANOSECONDS
      );
      if (message != null) {
        LOG.debug(
          "Received {} from {}",
          message.getClass().getSimpleName(),
          formatAddress(getMessageSource(message))
        );
      }
      return Optional.ofNullable(message);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for message", e);
      return Optional.empty();
    }
  }

  private SwarmAddress getMessageSource(
    StateMachineMessage message
  ) {
    return switch (message) {
      case InboundPingRequest req -> req.from();
      case InboundPingAck ack -> ack.from();
    };
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }

  /**
   * Enqueue an inbound wire message with a timeout.
   * Called by NetworkSimulator when delivering messages.
   * Unwraps the wire message to extract the payload.
   *
   * @param wireMessage the wire message to enqueue
   * @param timeout how long to wait if the queue is full
   * @return true if the message was enqueued, false if timeout expired
   * @throws InterruptedException if interrupted while waiting
   */
  boolean enqueue(WireMessage wireMessage, Duration timeout)
    throws InterruptedException {
    StateMachineMessage message = wireMessage.payload();
    boolean offered = inboundQueue.offer(
      message,
      timeout.toNanos(),
      TimeUnit.NANOSECONDS
    );
    if (!offered) {
      LOG.error(
        "Failed to enqueue message after timeout, queue is full. Message: {}",
        message
      );
    }
    return offered;
  }

  /**
   * Get the current queue size.
   * Primarily useful for testing and monitoring.
   *
   * @return the number of messages in the queue
   */
  public int queueSize() {
    return inboundQueue.size();
  }
}
