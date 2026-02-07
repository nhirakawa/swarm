package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequestResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineResponse;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Serialization;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
  public void send(StateMachineResponse response) {
    try {
      StateMachineMessage message = convertResponseToMessage(response);
      SwarmAddress targetAddress = getTargetAddress(response);

      // Serialize message to bytes
      byte[] payloadBytes = objectMapper.writeValueAsBytes(message);

      // Create header with actual payload length
      MessageHeader header = createHeader(
        message,
        localAddress,
        targetAddress,
        payloadBytes.length
      );

      WireMessage wireMessage = new WireMessage(
        localAddress,
        targetAddress,
        header,
        payloadBytes
      );

      boolean enqueued = networkSimulator.enqueue(wireMessage);
      if (enqueued) {
        LOG.debug(
          "Sent {} from {} to {}",
          message.getClass().getSimpleName(),
          formatAddress(localAddress),
          formatAddress(targetAddress)
        );
      }
    } catch (IOException e) {
      LOG.error("Failed to serialize message", e);
      throw new RuntimeException("Failed to serialize message", e);
    }
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }

  private StateMachineMessage convertResponseToMessage(
    StateMachineResponse response
  ) {
    return switch (response) {
      case PingRequestResponse pingRequest -> new InboundPingRequest(
        localAddress,
        pingRequest.onBehalfOf(),
        pingRequest.protocolPeriodId()
      );
      case PingAckResponse pingAck -> new InboundPingAck(
        localAddress,
        pingAck.proxyFor(),
        pingAck.protocolPeriodId()
      );
    };
  }

  private SwarmAddress getTargetAddress(StateMachineResponse response) {
    return switch (response) {
      case PingRequestResponse pingRequest -> pingRequest.target();
      case PingAckResponse pingAck -> pingAck.target();
    };
  }

  private MessageHeader createHeader(
    StateMachineMessage message,
    SwarmAddress source,
    SwarmAddress target,
    int payloadLength
  ) {
    SwarmMessageType messageType = switch (message) {
      case InboundPingRequest ignored -> SwarmMessageType.PING_REQUEST;
      case InboundPingAck ignored -> SwarmMessageType.PING_ACK;
    };

    byte[] sourceIp = extractIpBytes(source.address());
    byte[] targetIp = extractIpBytes(target.address());

    // Generate unique message ID
    long messageId = messageIdCounter.incrementAndGet();

    // Current timestamp in milliseconds
    long timestamp = System.currentTimeMillis();

    // Checksum placeholder - in-memory transport doesn't compute checksum
    // Real transports will compute CRC32 over header + payload
    long checksum = 0;

    return new MessageHeader(
      MessageVersion.V0,
      messageType,
      Compression.NONE,
      Serialization.JSON,
      payloadLength,
      messageId,
      timestamp,
      sourceIp,
      source.port(),
      targetIp,
      target.port(),
      checksum
    );
  }

  private byte[] extractIpBytes(String address) {
    try {
			return InetAddress.getByName(address).getAddress();
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException(
        "Invalid IP address: " + address,
        e
      );
    }
  }
}
