package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.fake.FakeTicker;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAck;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WaitingForAckProtocolStateTest {

  private static final SwarmAddress LOCAL = new InMemorySwarmAddress("local-1000");
  private static final SwarmAddress PING_TARGET = new InMemorySwarmAddress("host-2000");
  private static final SwarmAddress OTHER_NODE_1 = new InMemorySwarmAddress("host-3001");
  private static final SwarmAddress OTHER_NODE_2 = new InMemorySwarmAddress("host-3002");

  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
      .setLocalAddress(LOCAL)
      .setMulticastAddress(new InMemorySwarmAddress("MULTICAST"))
    .setFailureSubGroup(1)
    .setProtocolTick(Duration.ofMillis(100))
    .setMessageTimeout(Duration.ofMillis(20))
    .setProtocolPeriod(Duration.ofSeconds(1))
    .build();

  private FakeTicker ticker;

  private WaitingForAckProtocolState protocolState;

  @BeforeEach
  public void setup() {
    ticker = new FakeTicker();

    protocolState =
      new WaitingForAckProtocolState(
          new ProtocolStateContext(
              SWARM_CONFIG,
              4L,
              1L,
              Stopwatch.createStarted(ticker),
              new MemberRegistry(Set.of(PING_TARGET, OTHER_NODE_1, OTHER_NODE_2))
          ),
        PING_TARGET
      );
  }

  @Test
  public void itDoesNothingIfTickIsBeforeMessageTimeout() {
    Optional<Transition> transition = protocolState.applyTick();
    assertThat(transition).isEmpty();
  }

  @Test
  public void itTransitionsToWaitingForPingProxyAfterMessageTimeout() {
    // TODO @nhirakawa - make this test more robust
    ticker.write(SWARM_CONFIG.getMessageTimeout().toNanos() * 2);

    Optional<Transition> transition = protocolState.applyTick();

    assertThat(transition).isPresent();

    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForPingProxyProtocolState.class);
    assertThat(transition.get().getResponsesToSend())
      .hasSize(SWARM_CONFIG.getFailureSubGroup());
  }

  @Test
  public void itTransitionsToWaitingForNextProtocolPeriodAfterReceivingAck() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyPingAck(
      new PingAck(
        PING_TARGET,
        LOCAL,
        Optional.empty(),
        protocolState.context().protocolPeriodId()
      )
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForNextProtocolPeriodProtocolState.class);
    assertThat(transition.get().getResponsesToSend()).isEmpty();
  }
}
