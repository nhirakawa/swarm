package com.github.nhirakawa.swarm.config;

public enum ConfigPath {

  SWARM_PROTOCOL_PERIOD("swarm.protocol.period"),

  SWARM_MESSAGE_TIMEOUT("swarm.protocol.messageTimeout"),

  SWARM_FAILURE_SUBGROUP("swarm.protocol.failureSubGroup"),

  CLUSTER("swarm.cluster"),

  LOCAL("swarm.local"),

  ;

  private final String configPath;

  ConfigPath(String configPath) {
    this.configPath = configPath;
  }

  public String getConfigPath() {
    return configPath;
  }
}
