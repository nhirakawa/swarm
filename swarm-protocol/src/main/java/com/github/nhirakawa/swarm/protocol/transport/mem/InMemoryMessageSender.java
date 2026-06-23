package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.DiscoveryRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.header.Serialization;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.ThreadSafe;

import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of SwarmMessageSender.
 * Routes messages to other nodes via the network simulator.
 */
@ThreadSafe
public class InMemoryMessageSender implements SwarmMessageSender {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryMessageSender.class
  );

  private final SwarmAddress localAddress;
  private final NetworkSimulator networkSimulator;
  private final AtomicLong messageIdCounter;
  private final ObjectMapper objectMapper;

  public InMemoryMessageSender(
    SwarmAddress localAddress,
    NetworkSimulator networkSimulator
  ) {
    this.localAddress = localAddress;
    this.networkSimulator = networkSimulator;
    this.messageIdCounter = new AtomicLong(0);
    this.objectMapper = ObjectMapperWrapper.instance();
  }

  @Override
  public void send(StateMachineMessage message, Duration timeout) {
    try {
      if (message instanceof DiscoveryRequest discoveryRequest) {
        sendBroadcast(discoveryRequest, timeout);
      } else {
        sendUnicast(message, timeout);
      }
    } catch (IOException e) {
      LOG.error("Failed to serialize message", e);
      throw new RuntimeException("Failed to serialize message", e);
    }
  }

  private void sendBroadcast(DiscoveryRequest message, Duration timeout)
    throws IOException {
    // Serialize message to bytes (once for all targets)
    byte[] payloadBytes = objectMapper.writeValueAsBytes(message);

    MessageHeader header = createHeader(message, payloadBytes.length);
    WireMessage wireMessage = new WireMessage(localAddress, SwarmAddress.createMulticastAddress(), header, payloadBytes);
    networkSimulator.enqueue(wireMessage, timeout);

    LOG.debug("Sent multicast discovery request");
  }

  private void sendUnicast(StateMachineMessage message, Duration timeout) throws IOException {
    // Serialize message to bytes
    byte[] payloadBytes = objectMapper.writeValueAsBytes(message);

    // Create header with actual payload length
    MessageHeader header = createHeader(
      message,
      payloadBytes.length
    );

    WireMessage wireMessage = new WireMessage(
      localAddress,
      message.target(),
      header,
      payloadBytes
    );

    boolean enqueued = networkSimulator.enqueue(wireMessage, timeout);
    if (enqueued) {
      LOG.trace(
        "Sent {} source {} to {}",
        message.getClass().getSimpleName(),
        formatAddress(localAddress),
        formatAddress(message.target())
      );
    }
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }

  private MessageHeader createHeader(
    StateMachineMessage message,
    int payloadLength
  ) {
    // Generate unique message ID
    long messageId = messageIdCounter.incrementAndGet();

    // Current timestamp in milliseconds
    long timestamp = System.currentTimeMillis();

    // Checksum placeholder - in-memory transport doesn't compute checksum
    // Real transports will compute CRC32 over header + payload
    long checksum = 0;

    return new MessageHeader.Builder()
        .messageVersion(MessageVersion.V0)
        .type(message.type())
        .compression(Compression.NONE)
        .serialization(Serialization.JSON)
        .payloadLength(payloadLength)
        .messageId(messageId)
        .timestamp(timestamp)
        .checksum(checksum)
        .build();
  }
}
