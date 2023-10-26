package com.github.nhirakawa.swarm.protocol.util;

import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;

public class InjectableThreadLocalRandom extends InjectableRandom {

  @Inject
  public InjectableThreadLocalRandom() {}

  @Override
  protected int nextInt() {
    return ThreadLocalRandom
      .current()
      .nextInt(VALID_PERCENTS.lowerEndpoint(), VALID_PERCENTS.upperEndpoint());
  }
}
