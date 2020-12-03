package com.github.nhirakawa.swarm.protocol.protocol;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;

public class SwarmFailureInjector {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmFailureInjector.class
  );

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
      LOG.trace("Debug mode is disabled - not injecting failure");
      return false;
    }

    int rand = injectableRandom.getRandomInt();

    LOG.trace(
      "rand was {}, failure injection percent is {}",
      rand,
      swarmConfig.getFailureInjectionPercent()
    );

    return (rand < swarmConfig.getFailureInjectionPercent());
  }
}
