package com.github.nhirakawa.swarm.nio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;

import org.junit.Test;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingRequestMessage;

public class BaseSwarmMessageSerdeTest {
  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
    .setDebugEnabled(false)
    .setFailureInjectionPercent(0)
    .setSwarmStateBufferSize(1)
    .setProtocolPeriod(Duration.ofSeconds(1))
    .setProtocolTick(Duration.ofMillis(10))
    .setMessageTimeout(Duration.ofMillis(50))
    .setFailureSubGroup(1)
    .setLocalNode(SwarmNode.builder().setHost("host").setPort(1).build())
    .build();
  public static final SwarmNode FROM = SwarmNode
    .builder()
    .setHost("192.168.0.1")
    .setPort(1024)
    .build();
  public static final String UNIQUE_MESSAGE_ID = "asdf";
  public static final String PROTOCOL_PERIOD_ID = "fdsa";
  public static final SwarmNode PROXY = SwarmNode
    .builder()
    .setHost("localhost")
    .setPort(22)
    .build();

  private final BaseSwarmMessageSerde serde = new BaseSwarmMessageSerde(
    SWARM_CONFIG
  );

  @Test
  public void itRoundTripsPingRequestMessage() {
    PingRequestMessage serialized = PingRequestMessage
      .builder()
      .setFrom(FROM)
      .setTo(SWARM_CONFIG.getLocalNode())
      .setUniqueMessageId(UNIQUE_MESSAGE_ID)
      .setProtocolPeriodId(PROTOCOL_PERIOD_ID)
      .setOnBehalfOf(PROXY)
      .build();

    ByteBuffer buffer = serde.serialize(serialized);

    buffer.position(0);

    Optional<BaseSwarmMessage> deserialized = serde.deserialize(
      InetSocketAddress.createUnresolved(
        serialized.getFrom().getHost(),
        serialized.getFrom().getPort()
      ),
      buffer
    );

    assertThat(deserialized).contains(serialized);
  }

  @Test
  public void itRoundTripsPingAckMessage() {
    PingAckMessage serialized = PingAckMessage
      .builder()
      .setFrom(FROM)
      .setTo(SWARM_CONFIG.getLocalNode())
      .setUniqueMessageId(UNIQUE_MESSAGE_ID)
      .setProtocolPeriodId(PROTOCOL_PERIOD_ID)
      .setProxyFor(PROXY)
      .build();

    ByteBuffer buffer = serde.serialize(serialized);

    buffer.position(0);

    Optional<BaseSwarmMessage> deserialized = serde.deserialize(
      InetSocketAddress.createUnresolved(
       serialized.getFrom().getHost(),
       serialized.getFrom().getPort()
      ),
      buffer
    );

    assertThat(deserialized).contains(serialized);
  }
}
