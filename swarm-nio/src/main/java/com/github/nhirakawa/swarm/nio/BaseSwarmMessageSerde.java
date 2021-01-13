package com.github.nhirakawa.swarm.nio;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingRequestMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseSwarmMessageSerde {
  private static final Logger LOG = LoggerFactory.getLogger(
    BaseSwarmMessageSerde.class
  );

  private static final int MAGIC_NUMBER = 1937205613;

  private final SwarmConfig swarmConfig;

  @Inject
  BaseSwarmMessageSerde(SwarmConfig swarmConfig) {
    this.swarmConfig = swarmConfig;
  }

  public ByteBuffer serialize(BaseSwarmMessage swarmMessage) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);

    buffer.putInt(MAGIC_NUMBER);

    buffer.putInt(swarmMessage.getProtocolPeriodId().length());
    buffer.put(
      swarmMessage.getProtocolPeriodId().getBytes(StandardCharsets.UTF_8)
    );

    buffer.putInt(swarmMessage.getUniqueMessageId().length());
    buffer.put(
      swarmMessage.getUniqueMessageId().getBytes(StandardCharsets.UTF_8)
    );

    buffer.put(swarmMessage.getType().getId());

    if (swarmMessage instanceof PingAckMessage) {
      PingAckMessage pingAckMessage = (PingAckMessage) swarmMessage;

      if (pingAckMessage.getProxyFor().isPresent()) {
        SwarmNode proxyFor = pingAckMessage.getProxyFor().get();

        buffer.put((byte) 1);
        buffer.putInt(proxyFor.getHost().length());
        buffer.put(proxyFor.getHost().getBytes(StandardCharsets.UTF_8));
        buffer.putInt(proxyFor.getPort());
      } else {
        buffer.put((byte) 0);
      }
    } else if (swarmMessage instanceof PingRequestMessage) {
      PingRequestMessage pingRequestMessage = (PingRequestMessage) swarmMessage;

      if (pingRequestMessage.getOnBehalfOf().isPresent()) {
        SwarmNode onBehalfOf = pingRequestMessage.getOnBehalfOf().get();

        buffer.put((byte) 1);
        buffer.putInt(onBehalfOf.getHost().length());
        buffer.put(onBehalfOf.getHost().getBytes(StandardCharsets.UTF_8));
        buffer.putInt(onBehalfOf.getPort());
      } else {
        buffer.put((byte) 0);
      }
    }

    return buffer;
  }

  public Optional<BaseSwarmMessage> deserialize(
    InetSocketAddress from,
    ByteBuffer buffer
  ) {
    try {
      return deserializeInternal(from, buffer);
    } catch (Throwable t) {
      LOG.debug("Caught exception", t);
      return Optional.empty();
    }
  }

  private Optional<BaseSwarmMessage> deserializeInternal(
    InetSocketAddress from,
    ByteBuffer buffer
  ) {
    int magicNumber = buffer.getInt();

    if (magicNumber != MAGIC_NUMBER) {
      return Optional.empty();
    }

    int protocolPeriodIdLength = buffer.getInt();
    byte[] protocolPeriodIdBytes = new byte[protocolPeriodIdLength];
    buffer.get(protocolPeriodIdBytes);
    String protocolPeriodId = new String(
      protocolPeriodIdBytes,
      StandardCharsets.UTF_8
    );

    int uniqueMessageIdLength = buffer.getInt();
    byte[] uniqueMessageIdBytes = new byte[uniqueMessageIdLength];
    buffer.get(uniqueMessageIdBytes);
    String uniqueMessageId = new String(
      uniqueMessageIdBytes,
      StandardCharsets.UTF_8
    );

    Optional<SwarmMessageType> maybeSwarmMessageType = SwarmMessageType.fromId(
      buffer.get()
    );

    if (maybeSwarmMessageType.isEmpty()) {
      return Optional.empty();
    }

    SwarmNode fromSwarmNode = SwarmNode
      .builder()
      .setHost(from.getHostName())
      .setPort(from.getPort())
      .build();

    SwarmMessageType swarmMessageType = maybeSwarmMessageType.get();

    if (swarmMessageType == SwarmMessageType.PING_ACK) {
      PingAckMessage.Builder builder = PingAckMessage
        .builder()
        .setFrom(fromSwarmNode)
        .setTo(swarmConfig.getLocalNode())
        .setUniqueMessageId(uniqueMessageId)
        .setProtocolPeriodId(protocolPeriodId);

      byte flag = buffer.get();

      if (flag == 1) {
        int hostLength = buffer.getInt();
        byte[] hostBytes = new byte[hostLength];
        buffer.get(hostBytes);
        String host = new String(hostBytes, StandardCharsets.UTF_8);
        int port = buffer.getInt();

        SwarmNode proxyFor = SwarmNode
          .builder()
          .setHost(host)
          .setPort(port)
          .build();

        builder.setProxyFor(proxyFor);
      }

      return Optional.of(builder.build());
    } else if (swarmMessageType == SwarmMessageType.PING_REQUEST) {
      PingRequestMessage.Builder builder = PingRequestMessage
        .builder()
        .setFrom(fromSwarmNode)
        .setTo(swarmConfig.getLocalNode())
        .setUniqueMessageId(uniqueMessageId)
        .setProtocolPeriodId(protocolPeriodId);

      byte flag = buffer.get();

      if (flag == 1) {
        int hostLength = buffer.getInt();
        byte[] hostBytes = new byte[hostLength];
        buffer.get(hostBytes);
        String host = new String(hostBytes, StandardCharsets.UTF_8);
        int port = buffer.getInt();

        SwarmNode onBehalfOf = SwarmNode
          .builder()
          .setHost(host)
          .setPort(port)
          .build();

        builder.setOnBehalfOf(onBehalfOf);
      }

      return Optional.of(builder.build());
    } else {
      return Optional.empty();
    }
  }
}
