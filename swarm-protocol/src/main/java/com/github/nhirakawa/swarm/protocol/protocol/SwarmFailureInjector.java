package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import javax.inject.Inject;

public class SwarmFailureInjector {
  private final SwarmConfig swarmConfig;
  private final InjectableRandom injectableRandom;

  @Inject
  SwarmFailureInjector(
    SwarmConfig swarmConfig,
    InjectableRandom injectableRandom
  ) {
    this.swarmConfig = swarmConfig;
    this.injectableRandom = injectableRandom;
  }

  boolean shouldInjectFailure() {
    if (!swarmConfig.isDebugEnabled()) {
      return false;
    }

    int rand = injectableRandom.getRandomInt();

    return (rand < swarmConfig.getFailureInjectionPercent());
  }
}
