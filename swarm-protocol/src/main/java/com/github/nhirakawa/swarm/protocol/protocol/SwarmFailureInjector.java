package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.ConfigPath;
import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import com.typesafe.config.Config;
import javax.inject.Inject;

public class SwarmFailureInjector {
  private final Config config;
  private final InjectableRandom injectableRandom;

  @Inject
  SwarmFailureInjector(Config config, InjectableRandom injectableRandom) {
    this.config = config;
    this.injectableRandom = injectableRandom;
  }

  boolean shouldInjectFailure() {
    if (!config.getBoolean(ConfigPath.DEBUG_ENABLED.getConfigPath())) {
      return false;
    }

    int rand = injectableRandom.getRandomInt();

    return (
      rand < config.getInt(ConfigPath.FAILURE_INJECTION_PERCENT.getConfigPath())
    );
  }
}
