package com.github.nhirakawa.swarm.runner.config;

import com.google.common.base.Preconditions;
import com.typesafe.config.Config;

public final class ConfigValidator {

  private ConfigValidator() {}

  public static Config validate(Config config) {
    for (ConfigPath configPath : ConfigPath.values()) {
      Preconditions.checkState(
        config.hasPath(configPath.getConfigPath()),
        "Could not find config path %s (%s)",
        configPath,
        configPath.getConfigPath()
      );
    }

    return config;
  }
}
