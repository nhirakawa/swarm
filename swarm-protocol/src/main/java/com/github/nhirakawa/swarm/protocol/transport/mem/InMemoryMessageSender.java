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

  public InMemoryMessageSender(
    SwarmAddress localAddress,
    NetworkSimulator networkSimulator
  ) {
    this.localAddress = localAddress;
    this.networkSimulator = networkSimulator;
    this.messageIdCounter = new AtomicLong(0);
  }

  @Override
  public void send(StateMachineResponse response) {
    StateMachineMessage message = convertResponseToMessage(response);
    SwarmAddress targetAddress = getTargetAddress(response);
    MessageHeader header = createHeader(message, localAddress, targetAddress);

    WireMessage wireMessage = new WireMessage(
      localAddress,
      targetAddress,
      header,
      message
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
    SwarmAddress target
  ) {
    SwarmMessageType messageType = switch (message) {
      case InboundPingRequest ignored -> SwarmMessageType.PING_REQUEST;
      case InboundPingAck ignored -> SwarmMessageType.PING_ACK;
    };

    byte[] sourceIp = extractIpBytes(source.address());
    byte[] targetIp = extractIpBytes(target.address());

    // In-memory transport doesn't serialize, so use 0 as placeholder
    // Real transports will calculate actual payload length
    int payloadLength = 0;

    // Generate unique message ID
    long messageId = messageIdCounter.incrementAndGet();

    // Current timestamp in milliseconds
    long timestamp = System.currentTimeMillis();

    // Checksum placeholder - in-memory transport doesn't need checksum
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
