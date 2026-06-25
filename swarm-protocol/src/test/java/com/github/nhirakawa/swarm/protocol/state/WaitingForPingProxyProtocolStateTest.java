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

public class WaitingForPingProxyProtocolStateTest {

  private static final SwarmAddress LOCAL = new InMemorySwarmAddress("local");
  private static final SwarmAddress TARGET = new InMemorySwarmAddress("host-2000");
  private static final SwarmAddress OTHER_1 = new InMemorySwarmAddress("host-3001");
  private static final SwarmAddress OTHER_2 = new InMemorySwarmAddress("host-3002");

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

  private WaitingForPingProxyProtocolState protocolState;

  @BeforeEach
  public void setup() {
    ticker = new FakeTicker();

    protocolState =
      new WaitingForPingProxyProtocolState(
        SWARM_CONFIG,
        4L,
        1L,
        Stopwatch.createStarted(ticker),
        new MemberRegistry(Set.of(TARGET, OTHER_1, OTHER_2)),
        TARGET,
        Set.of(OTHER_1)
      );
  }

  @Test
  public void itTransitionsToWaitingForNextProtocolPeriodAfterProxyAck() {
    // TODO @nhirakawa - make this test more robust
    Optional<Transition> transition = protocolState.applyPingAck(
      new PingAck(OTHER_1, LOCAL, Optional.of(TARGET), 4L)
    );

    assertThat(transition).isPresent();

    assertThat(transition.get().getResponsesToSend()).isEmpty();
    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForNextProtocolPeriodProtocolState.class);
  }

  @Test
  public void itTransitionsToWaitingForNextProtocolPeriodAfterProtocolTimeout() {
    ticker.write(SWARM_CONFIG.getProtocolPeriod().toNanos() * 2);

    Optional<Transition> transition = protocolState.applyTick();

    assertThat(transition).isPresent();

    assertThat(transition.get().getResponsesToSend()).isEmpty();
    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForNextProtocolPeriodProtocolState.class);

    WaitingForNextProtocolPeriodProtocolState nextState = (WaitingForNextProtocolPeriodProtocolState) transition
      .get()
      .getNextSwarmProtocolState();

    assertThat(nextState.protocolPeriodId)
      .isEqualTo(protocolState.protocolPeriodId);
  }

  @Test
  public void itDoesNothingIfAckIsNotFromProxy() {
    Optional<Transition> transition = protocolState.applyPingAck(
      new PingAck(OTHER_2, LOCAL, Optional.of(TARGET), 4L)
    );

    assertThat(transition).isEmpty();
  }

  @Test
  public void itDoesNothingIfAckIsNotForTarget() {
    Optional<Transition> transition = protocolState.applyPingAck(
      new PingAck(OTHER_1, LOCAL, Optional.of(OTHER_2), 4L)
    );

    assertThat(transition).isEmpty();
  }
}
