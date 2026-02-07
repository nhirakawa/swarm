package com.github.nhirakawa.swarm.protocol.transport.mem;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * A message wrapper that supports delayed delivery for network simulation.
 */
record DelayedMessage(WireMessage wireMessage, long deliveryTimeNanos)
  implements Delayed {
  @Override
  public long getDelay(TimeUnit unit) {
    long delayNanos = deliveryTimeNanos - System.nanoTime();
    return unit.convert(delayNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public int compareTo(Delayed other) {
    if (other instanceof DelayedMessage otherMsg) {
      return Long.compare(deliveryTimeNanos, otherMsg.deliveryTimeNanos);
    }
    return Long.compare(
      getDelay(TimeUnit.NANOSECONDS),
      other.getDelay(TimeUnit.NANOSECONDS)
    );
  }
}
