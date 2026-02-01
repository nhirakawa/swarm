package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.fake.FakeTicker;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.Transition;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WaitingForNextProtocolPeriodStateTest {

  private static final SwarmAddress LOCAL = new SwarmAddress(
    "host",
    1000,
    "host-1000"
  );
  private static final SwarmAddress OTHER = new SwarmAddress(
    "host",
    2000,
    "host-2000"
  );

  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
      .setLocalAddress(LOCAL)
      .addInitialClusterMembership(OTHER)
    .setProtocolPeriod(Duration.ofSeconds(1))
    .setProtocolTick(Duration.ofMillis(100))
    .setMessageTimeout(Duration.ofMillis(200))
    .setFailureSubGroup(1)
    .build();

  private WaitingForNextProtocolPeriodProtocolState protocolState;

  private FakeTicker ticker;

  @BeforeEach
  public void setup() {
    this.ticker = new FakeTicker();

    protocolState =
      new WaitingForNextProtocolPeriodProtocolState(
        SWARM_CONFIG,
        "protocol period id",
        Stopwatch.createStarted(ticker),
        new MemberRegistry(Set.of(OTHER))
      );
  }

  @Test
  public void itDoesNothingIfProtocolPeriodHasNotEnded() {
    Optional<Transition> transition = protocolState.applyTick();

    assertThat(transition).isEmpty();
  }

  @Test
  public void itTransitionsToWaitingForAckAfterNewProtocolPeriodStarts() {
    // TODO @nhirakawa - make this test more robust
    ticker.write(SWARM_CONFIG.getProtocolPeriod().toNanos() * 2);

    Optional<Transition> transition = protocolState.applyTick();

    assertThat(transition).isPresent();

    assertThat(transition.get().getResponsesToSend()).hasSize(1);
    assertThat(transition.get().getNextSwarmProtocolState())
      .isInstanceOf(WaitingForAckProtocolState.class);
  }

  @Test
  public void itIgnoresAck() {
    Optional<Transition> transition = protocolState.applyPingAck(
      new InboundPingAck(
        OTHER,
        Optional.empty(),
        protocolState.protocolPeriodId
      )
    );

    assertThat(transition).isEmpty();
  }
}
