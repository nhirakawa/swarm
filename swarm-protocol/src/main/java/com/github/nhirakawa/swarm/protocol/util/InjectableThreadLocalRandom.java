package com.github.nhirakawa.swarm.protocol.util;

import java.util.concurrent.ThreadLocalRandom;

public class InjectableThreadLocalRandom extends InjectableRandom {

  @Override
  protected int nextInt() {
    return ThreadLocalRandom.current().nextInt(VALID_PERCENTS.lowerEndpoint(), VALID_PERCENTS.upperEndpoint());
  }
}
