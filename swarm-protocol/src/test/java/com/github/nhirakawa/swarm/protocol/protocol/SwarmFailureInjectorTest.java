package com.github.nhirakawa.swarm.protocol.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import com.typesafe.config.Config;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SwarmFailureInjectorTest {
  private static final AtomicInteger RANDOM = new AtomicInteger(0);
  private static final int NUMBER_OF_SAMPLES = 100_000;

  private SwarmFailureInjector swarmFailureInjector;

  @Before
  public void setup() {
    RANDOM.set(0);

    Config config = mock(Config.class);
    when(
        config.getInt(
          Mockito.eq(ConfigPath.FAILURE_INJECTION_PERCENT.getConfigPath())
        )
      )
      .thenAnswer(ignored -> RANDOM.get());
    when(
        config.getBoolean(Mockito.eq(ConfigPath.DEBUG_ENABLED.getConfigPath()))
      )
      .thenReturn(true);

    swarmFailureInjector =
      new SwarmFailureInjector(config, new InjectableThreadLocalRandom());
  }

  @Test
  public void testFailureInjectionWhenDisabled() {
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
    RANDOM.set(100);

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
    RANDOM.set(56);

    int failures = 0;

    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      if (swarmFailureInjector.shouldInjectFailure()) {
        failures++;
      }
    }

    double failurePercent = ((double) failures / NUMBER_OF_SAMPLES) * 100;

    assertThat(failurePercent).isCloseTo(RANDOM.get(), Offset.offset(1.0));
  }
}
