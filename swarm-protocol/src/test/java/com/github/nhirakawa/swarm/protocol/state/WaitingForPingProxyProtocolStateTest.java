package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.protocol.Transition;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class WaitingForPingProxyProtocolStateTest {
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

  private WaitingForPingProxyProtocolState protocolState;

  @Before
  public void setup() {
    protocolState =
      new WaitingForPingProxyProtocolState(
        TIMESTAMP,
        SWARM_CONFIG,
        "protocol-period-id",
        PING_TARGET,
        ImmutableSet.of(OTHER_NODE_1)
      );
  }

  @Test
  public void itTransitionsToWaitingForNextProtocolPeriodAfterProxyAck() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyPingAck(
      PingAckMessage
        .builder()
        .setProtocolPeriodId("protocol-period-id")
        .setUniqueMessageId("asdf")
        .setFrom(OTHER_NODE_1)
        .setTo(LOCAL_NODE)
        .setProxyFor(PING_TARGET)
        .build()
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getMessagesToSend()).isEmpty();
    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForNextProtocolPeriodProtocolState.class);
  }

  @Test
  public void itDoesNothingIfAckIsNotFromProxy() {
    Optional<Transition> transition = protocolState.applyPingAck(
      PingAckMessage
        .builder()
        .setProtocolPeriodId("protocol-period-id")
        .setUniqueMessageId("asdf")
        .setFrom(OTHER_NODE_2)
        .setTo(LOCAL_NODE)
        .setProxyFor(PING_TARGET)
        .build()
    );

    assertThat(transition).isEmpty();
  }

  @Test
  public void itDoesNothingIfAckIsNotForTarget() {
    Optional<Transition> transition = protocolState.applyPingAck(
      PingAckMessage
        .builder()
        .setProtocolPeriodId("protocol-period-id")
        .setUniqueMessageId("asdf")
        .setFrom(OTHER_NODE_1)
        .setTo(LOCAL_NODE)
        .setProxyFor(OTHER_NODE_2)
        .build()
    );

    assertThat(transition).isEmpty();
  }
}
