package com.github.nhirakawa.swarm.protocol.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

public abstract class InjectableRandom {
  protected static final Range<Integer> VALID_PERCENTS = Range.closed(1, 100);

  protected abstract int nextInt();

  public int getRandomInt() {
    int nextInt = nextInt();
    Preconditions.checkArgument(
      VALID_PERCENTS.contains(nextInt),
      "integer is not in range %s",
      VALID_PERCENTS
    );
    return nextInt;
  }
}
