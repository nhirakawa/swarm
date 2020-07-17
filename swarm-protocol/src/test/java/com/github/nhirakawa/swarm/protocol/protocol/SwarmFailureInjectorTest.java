package com.github.nhirakawa.swarm.protocol.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import java.time.Duration;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.Test;

public class SwarmFailureInjectorTest {
  private static final int NUMBER_OF_SAMPLES = 100_000;

  private static final SwarmConfig BASE = SwarmConfig
    .builder()
    .setProtocolPeriod(Duration.ofMillis(1))
    .setMessageTimeout(Duration.ofMillis(1))
    .setSwarmStateBufferSize(0)
    .setDebugEnabled(true)
    .setFailureInjectionPercent(0)
    .setEntireClusterLocal(true)
    .setLocalNode(
      SwarmNode
        .builder()
        .setHost("host")
        .setPort(1)
        .setUniqueId(UUID.randomUUID())
        .build()
    )
    .setProtocolTick(10)
    .setFailureSubGroup(1)
    .build();

  @Test
  public void testFailureInjectionWhenDisabled() {
    SwarmFailureInjector swarmFailureInjector = new SwarmFailureInjector(
      BASE,
      new InjectableThreadLocalRandom()
    );
    int failures = 0;

    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      if (swarmFailureInjector.shouldInjectFailure()) {
        failures++;
      }
    }

    assertThat(failures).isZero();
  }

  @Test
  public void testFailureInjectionWhenFullyEnabled() {
    SwarmFailureInjector swarmFailureInjector = new SwarmFailureInjector(
      BASE.withFailureInjectionPercent(100),
      new InjectableThreadLocalRandom()
    );

    int failures = 0;

    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      if (swarmFailureInjector.shouldInjectFailure()) {
        failures++;
      }
    }

    assertThat(failures).isEqualTo(NUMBER_OF_SAMPLES);
  }

  @Test
  public void testFailureInjectionWhenPartiallyEnabled() {
    SwarmConfig swarmConfig = BASE.withFailureInjectionPercent(56);
    SwarmFailureInjector swarmFailureInjector = new SwarmFailureInjector(
      swarmConfig,
      new InjectableThreadLocalRandom()
    );

    int failures = 0;

    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      if (swarmFailureInjector.shouldInjectFailure()) {
        failures++;
      }
    }

    double failurePercent = ((double) failures / NUMBER_OF_SAMPLES) * 100;

    assertThat(failurePercent)
      .isCloseTo(swarmConfig.getFailureInjectionPercent(), Offset.offset(1.0));
  }
}
