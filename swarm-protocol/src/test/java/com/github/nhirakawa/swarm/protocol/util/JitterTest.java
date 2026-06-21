package com.github.nhirakawa.swarm.protocol.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JitterTest {

  @Test
  void testApply_returnsValueInExpectedRange() {
    Duration baseDuration = Duration.ofMillis(100);
    Duration jitterRange = Duration.ofMillis(10);

    // Run multiple times to test randomness
    for (int i = 0; i < 100; i++) {
      Duration jittered = Jitter.apply(baseDuration, jitterRange);

      // Should be between 90ms and 110ms
      assertThat(jittered.toMillis()).isBetween(90L, 110L);
    }
  }

  @Test
  void testApplyJitter_withZero() {
    Duration baseDuration = Duration.ofMillis(100);
    Duration jitterRange = Duration.ZERO;

    Duration jittered = Jitter.apply(baseDuration, jitterRange);

    assertThat(jittered).isEqualTo(baseDuration);
  }

  @Test
  void testApply_doesNotGoNegative() {
    Duration baseDuration = Duration.ofMillis(10);
    Duration jitterRange = Duration.ofMillis(20); // Larger than base

    // Run multiple times to ensure we never go negative
    for (int i = 0; i < 100; i++) {
      Duration jittered = Jitter.apply(baseDuration, jitterRange);

      assertThat(jittered.toNanos()).isGreaterThanOrEqualTo(0);
    }
  }

  @Test
  void testApply_producesVariableResults() {
    Duration baseDuration = Duration.ofMillis(100);
    Duration jitterRange = Duration.ofMillis(20);

    Duration first = Jitter.apply(baseDuration, jitterRange);
    boolean foundDifferent = false;

    // Run up to 50 times to find a different value
    for (int i = 0; i < 50; i++) {
      Duration next = Jitter.apply(baseDuration, jitterRange);
      if (!next.equals(first)) {
        foundDifferent = true;
        break;
      }
    }

    assertThat(foundDifferent)
      .as("Should produce different jittered values")
      .isTrue();
  }

  @Test
  void testApply_withSmallDurations() {
    Duration baseDuration = Duration.ofNanos(1000);
    Duration jitterRange = Duration.ofNanos(100);

    Duration jittered = Jitter.apply(baseDuration, jitterRange);

    assertThat(jittered.toNanos()).isBetween(900L, 1100L);
  }

  @Test
  void testApply_withLargeDurations() {
    Duration baseDuration = Duration.ofSeconds(10);
    Duration jitterRange = Duration.ofSeconds(1);

    Duration jittered = Jitter.apply(baseDuration, jitterRange);

    assertThat(jittered.toSeconds()).isBetween(9L, 11L);
  }
}
