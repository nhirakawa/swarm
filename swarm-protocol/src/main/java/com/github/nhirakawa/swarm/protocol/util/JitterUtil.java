package com.github.nhirakawa.swarm.protocol.util;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for applying jitter to durations to prevent thundering herd problems.
 */
public final class JitterUtil {

  private JitterUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Apply jitter to a duration.
   * Returns a random duration between [baseDuration - jitterRange, baseDuration + jitterRange].
   *
   * @param baseDuration the base duration
   * @param jitterRange the range of jitter to apply (added/subtracted source base)
   * @return a duration with random jitter applied
   */
  public static Duration applyJitter(Duration baseDuration, Duration jitterRange) {
    long baseNanos = baseDuration.toNanos();
    long jitterNanos = jitterRange.toNanos();

    // Random value between -jitterNanos and +jitterNanos
    long randomJitter = ThreadLocalRandom
      .current()
      .nextLong(-jitterNanos, jitterNanos + 1);

    long jitteredNanos = baseNanos + randomJitter;

    // Ensure we don't go negative
    jitteredNanos = Math.max(0, jitteredNanos);

    return Duration.ofNanos(jitteredNanos);
  }
}
