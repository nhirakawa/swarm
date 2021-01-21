package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.Transition;

public class WaitingForAckProtocolStateTest {
  private static final Instant TIMESTAMP = Instant.ofEpochMilli(1000);

  private static final SwarmNode LOCAL_NODE = SwarmNode
    .builder()
    .setHost("local")
    .setPort(1000)
    .build();

  private static final SwarmNode PING_TARGET = SwarmNode
    .builder()
    .setHost("host")
    .setPort(2000)
    .build();

  private static final SwarmNode OTHER_NODE_1 = SwarmNode
    .builder()
    .setHost("host")
    .setPort(3001)
    .build();
  private static final SwarmNode OTHER_NODE_2 = SwarmNode
    .builder()
    .setHost("host")
    .setPort(3002)
    .build();

  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
    .addClusterNodes(PING_TARGET, OTHER_NODE_1, OTHER_NODE_2)
    .setDebugEnabled(false)
    .setFailureInjectionPercent(0)
    .setFailureSubGroup(1)
    .setLocalNode(LOCAL_NODE)
    .setProtocolTick(Duration.ofMillis(100))
    .setMessageTimeout(Duration.ofMillis(20))
    .setSwarmStateBufferSize(1)
    .setProtocolPeriod(Duration.ofSeconds(1))
    .build();

  private WaitingForAckProtocolState protocolState;

  @Before
  public void setup() {
    protocolState =
      new WaitingForAckProtocolState(
        TIMESTAMP,
        SWARM_CONFIG,
        PING_TARGET,
        "protocol-period-id"
      );
  }

  @Test
  public void itDoesNothingIfTickIsBeforeMessageTimeout() {
    Optional<Transition> transition = protocolState.applyTick(
      SwarmTimeoutMessage
        .builder()
        .setTimestamp(TIMESTAMP.plusMillis(10))
        .build()
    );

    assertThat(transition).isEmpty();
  }

  @Test
  public void itTransitionsToWaitingForPingProxyAfterMessageTimeout() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyTick(
      SwarmTimeoutMessage
        .builder()
        .setTimestamp(TIMESTAMP.plus(Duration.ofMillis(30)))
        .build()
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForPingProxyProtocolState.class);
    assertThat(transition.get().getMessagesToSend())
      .hasSize(SWARM_CONFIG.getFailureSubGroup());
  }

  @Test
  public void itTransitionsToWaitingForNextProtocolPeriodAfterReceivingAck() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyPingAck(
      PingAckMessage
        .builder()
        .setFrom(PING_TARGET)
        .setTo(LOCAL_NODE)
        .setProtocolPeriodId(protocolState.protocolPeriodId)
        .setUniqueMessageId(UUID.randomUUID().toString())
        .build()
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForNextProtocolPeriodProtocolState.class);
    assertThat(transition.get().getMessagesToSend()).isEmpty();
  }
}
