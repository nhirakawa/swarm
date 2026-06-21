package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
  boolean enqueue(WireMessage wireMessage, Duration timeout) {
    SwarmAddress source = wireMessage.source();
    SwarmAddress target = wireMessage.target();

    // Simulate packet loss on send
    if (config.shouldDropOnSend(source, target)) {
      LOG.debug(
        "Dropped message on send source {} to {}",
        formatAddress(source),
        formatAddress(target)
      );
      return false;
    }

    if (isMulticast(target)) {
      return multicast(wireMessage, timeout);
    } else {
      return unicast(wireMessage, timeout);
    }
  }

  private boolean multicast(WireMessage wireMessage, Duration timeout) {
    boolean deliveredAny = false;
    ImmutableSet<SwarmAddress> keys = registry.keys();
    LOG.trace("Multicasting from {} - {} node(s) in registry", formatAddress(wireMessage.source()), keys.size());

    for (SwarmAddress target : keys) {
      if (target.equals(wireMessage.source())) {
        continue;
      }

      Optional<byte[]> targetIp = registry.resolve(target);

      if (targetIp.isEmpty()) {
        LOG.debug("Could not resolve address for {}", target);
        continue;
      }

      MessageHeader messageHeader = new MessageHeader(
          wireMessage.header().messageVersion(),
          wireMessage.header().type(),
          wireMessage.header().compression(),
          wireMessage.header().serialization(),
          wireMessage.header().payloadLength(),
          wireMessage.header().messageId(),
          wireMessage.header().timestamp(),
          wireMessage.header().sourceIp(),
          wireMessage.header().sourcePort(),
          targetIp.get(),
          wireMessage.header().targetPort(),
          0L
      );

      WireMessage unicastWireMessage = new WireMessage(wireMessage.source(), target, messageHeader, wireMessage.payload());

      boolean unicastWasSuccessful = unicast(unicastWireMessage, timeout);

      deliveredAny = deliveredAny || unicastWasSuccessful;
    }

    return deliveredAny;
  }

  private boolean unicast(WireMessage wireMessage, Duration timeout) {
    Duration latency = config.sampleLatency(wireMessage.source(), wireMessage.target());
    long deliveryTimeNanos = System.nanoTime() + latency.toNanos();

    DelayedMessage delayed = new DelayedMessage(wireMessage, deliveryTimeNanos);

    return delayQueue.offer(delayed, timeout.toNanos(), TimeUnit.NANOSECONDS);
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
      LOG.trace("Drainer dequeued {} from {} to {}", wireMessage.header().type(), formatAddress(source), formatAddress(target));

      // Simulate packet loss in transit
      if (config.shouldDropInTransit(source, target)) {
        LOG.debug(
          "Dropped message in transit source {} to {}",
          formatAddress(source),
          formatAddress(target)
        );
        continue;
      }

      deliverToReceiver(wireMessage);
    }

    LOG.info("Network simulator drainer thread stopped");
  }

  private void deliverToReceiver(WireMessage wireMessage) throws InterruptedException {
    SwarmAddress target = wireMessage.target();

    if (isMulticast(target)) {
      for (SwarmAddress unicastTarget : registry.keys()) {
        InMemoryTransport inMemoryTransport = registry.lookup(unicastTarget).orElseThrow();
        inMemoryTransport.enqueue(wireMessage, RECEIVER_ENQUEUE_TIMEOUT);
			}
    } else {
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

        boolean enqueued = receiver.enqueue(
            wireMessage,
            RECEIVER_ENQUEUE_TIMEOUT
        );

			if (enqueued) {
					LOG.trace("Delivered {} message to {}:{}", wireMessage.header().type(), wireMessage.header().targetIp(), wireMessage.header().targetPort());
			} else {
				LOG.warn(
						"Failed to enqueue message to receiver at {} after timeout",
						target
				);
			}
		}
  }

  private boolean isMulticast(SwarmAddress address) {
    try {
      return InetAddress.getByName(address.address()).isMulticastAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }
}
