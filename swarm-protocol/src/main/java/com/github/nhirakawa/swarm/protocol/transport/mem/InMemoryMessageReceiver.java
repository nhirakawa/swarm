package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.fasterxml.jackson.databind.ObjectReader;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.io.IOException;
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
  private final ObjectReader objectReader;

  public InMemoryMessageReceiver(int queueCapacity, ObjectReader objectReader) {
    this.inboundQueue = new LinkedBlockingQueue<>(queueCapacity);
    this.objectReader = objectReader;
  }

  @Override
  public Optional<StateMachineMessage> receive(Duration timeout) {
    try {
      StateMachineMessage message = inboundQueue.poll(
        timeout.toNanos(),
        TimeUnit.NANOSECONDS
      );
      if (message != null) {
        LOG.trace(
          "Received {} source {}",
          message.getClass().getSimpleName(),
          message.source().asString()
        );
      }
      return Optional.ofNullable(message);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for message", e);
      return Optional.empty();
    }
  }

  /**
   * Enqueue an inbound wire message with a timeout.
   * Called by NetworkSimulator when delivering messages.
   * Deserializes the wire message payload and enqueues it.
   *
   * @param wireMessage the wire message to enqueue
   * @param timeout how long to wait if the queue is full
   * @return true if the message was enqueued, false if timeout expired
   * @throws InterruptedException if interrupted while waiting
   */
  boolean enqueue(WireMessage wireMessage, Duration timeout)
    throws InterruptedException {
    try {
      // Deserialize payload based on message type in header
      StateMachineMessage message = deserializePayload(
        wireMessage.payload(),
        wireMessage.header().type()
      );

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
    } catch (IOException e) {
      LOG.error("Failed to deserialize message", e);
      throw new RuntimeException("Failed to deserialize message", e);
    }
  }

  private StateMachineMessage deserializePayload(
    byte[] payloadBytes,
    SwarmMessageType messageType
  ) throws IOException {
    return switch (messageType) {
      case PING_REQUEST -> objectReader.readValue(
        payloadBytes,
        PingRequest.class
      );
      case PING_ACK -> objectReader.readValue(
        payloadBytes,
        PingAck.class
      );
      case DISCOVERY_REQUEST -> objectReader.readValue(
        payloadBytes,
        DiscoveryRequest.class
      );
      case DISCOVERY_RESPONSE -> objectReader.readValue(
        payloadBytes,
        DiscoveryResponse.class
      );
    };
  }

  /**
   * Get the current queue size.
   * Primarily useful for testing and monitoring.
   *
   * @return the number of messages in the queue
   */
  int queueSize() {
    return inboundQueue.size();
  }
}
