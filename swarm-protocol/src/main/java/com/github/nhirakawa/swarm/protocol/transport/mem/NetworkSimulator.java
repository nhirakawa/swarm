package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simulates network conditions including latency and packet loss.
 * Messages are queued with a delay and drained by a background thread.
 */
public class NetworkSimulator extends AbstractExecutionThreadService {

  private static final Logger LOG = LogManager.getLogger(
    NetworkSimulator.class
  );

  private static final Duration RECEIVER_ENQUEUE_TIMEOUT = Duration.ofMillis(
    100
  );

  private final DelayQueue<DelayedMessage> delayQueue;
  private final InMemoryTransportRegistry registry;
  private final NetworkSimulationConfig config;

  public NetworkSimulator(
    InMemoryTransportRegistry registry,
    NetworkSimulationConfig config
  ) {
    this.delayQueue = new DelayQueue<>();
    this.registry = registry;
    this.config = config;
  }

  /**
   * Enqueue a wire message for delivery with simulated network conditions.
   *
   * @param wireMessage the wire message to deliver
   * @return true if message was enqueued, false if dropped during send
   */
  public boolean enqueue(WireMessage wireMessage) {
    SwarmAddress source = wireMessage.source();
    SwarmAddress target = wireMessage.target();

    // Simulate packet loss on send
    if (config.shouldDropOnSend(source, target)) {
      LOG.debug(
        "Dropped message on send from {} to {}",
        formatAddress(source),
        formatAddress(target)
      );
      return false;
    }

    Duration latency = config.sampleLatency(source, target);
    long deliveryTimeNanos = System.nanoTime() + latency.toNanos();

    DelayedMessage delayed = new DelayedMessage(wireMessage, deliveryTimeNanos);

    return delayQueue.offer(delayed);
  }

  @Override
  protected void run() throws Exception {
    LOG.info("Network simulator drainer thread started");

    while (isRunning()) {
      DelayedMessage delayed = delayQueue.poll(5, TimeUnit.MILLISECONDS);

      if (delayed == null) {
        continue;
      }

      WireMessage wireMessage = delayed.wireMessage();
      SwarmAddress source = wireMessage.source();
      SwarmAddress target = wireMessage.target();

      // Simulate packet loss in transit
      if (config.shouldDropInTransit(source, target)) {
        LOG.debug(
          "Dropped message in transit from {} to {}",
          formatAddress(source),
          formatAddress(target)
        );
        continue;
      }

      deliverToReceiver(wireMessage);
    }

    LOG.info("Network simulator drainer thread stopped");
  }

  private void deliverToReceiver(WireMessage wireMessage) {
    SwarmAddress target = wireMessage.target();
    Optional<InMemoryTransport> maybeTransport = registry.lookup(target);

    if (maybeTransport.isEmpty()) {
      LOG.warn(
        "No transport registered for target address: {}. Message dropped.",
        target
      );
      return;
    }

    InMemoryTransport transport = maybeTransport.get();
    InMemoryMessageReceiver receiver = transport.receiver();

    try {
      boolean enqueued = receiver.enqueue(
        wireMessage,
        RECEIVER_ENQUEUE_TIMEOUT
      );

      if (!enqueued) {
        LOG.warn(
          "Failed to enqueue message to receiver at {} after timeout. Queue full or slow processing.",
          target
        );
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while enqueueing to receiver at {}", target);
    }
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }
}
