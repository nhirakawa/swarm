package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.protocol.Transition;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class WaitingForNextProtocolPeriodStateTest {
  private static final Instant TIMESTAMP = Instant.ofEpochMilli(1000);

  private static final SwarmNode LOCAL_SWARM_NODE = SwarmNode
    .builder()
    .setHost("host")
    .setPort(1000)
    .setUniqueId(UUID.randomUUID())
    .build();

  private static final SwarmNode OTHER_NODE = SwarmNode
    .builder()
    .setHost("host")
    .setPort(2000)
    .setUniqueId(UUID.randomUUID())
    .build();

  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
    .setProtocolPeriod(Duration.ofSeconds(1))
    .setProtocolTick(Duration.ofMillis(100))
    .setMessageTimeout(Duration.ofMillis(200))
    .setSwarmStateBufferSize(1)
    .setLocalNode(LOCAL_SWARM_NODE)
    .addClusterNodes(OTHER_NODE)
    .setFailureSubGroup(1)
    .setDebugEnabled(false)
    .setFailureInjectionPercent(0)
    .build();

  private WaitingForNextProtocolPeriodProtocolState protocolState;

  @Before
  public void setup() {
    protocolState =
      new WaitingForNextProtocolPeriodProtocolState(
        TIMESTAMP,
        SWARM_CONFIG,
        "protocol period id"
      );
  }

  @Test
  public void itDoesNothingIfProtocolPeriodHasNotEnded() {
    Optional<Transition> transition = protocolState.applyTick(
      SwarmTimeoutMessage
        .builder()
        .setTimestamp(TIMESTAMP.plus(Duration.ofMillis(100)))
        .build()
    );

    assertThat(transition).isEmpty();
  }

  @Test
  public void itTransitionsToWaitingForAckAfterNewProtocolPeriodStarts() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyTick(
      SwarmTimeoutMessage
        .builder()
        .setTimestamp(TIMESTAMP.plus(Duration.ofSeconds(2)))
        .build()
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getMessagesToSend()).hasSize(1);
    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForAckProtocolState.class);
  }

  @Test
  public void itIgnoresAck() {
    Optional<Transition> transition = protocolState.applyPingAck(
      PingAckMessage
        .builder()
        .setProtocolPeriodId(protocolState.protocolPeriodId)
        .setUniqueMessageId("asdf")
        .setFrom(OTHER_NODE)
        .build()
    );

    assertThat(transition).isEmpty();
  }
}
