package com.github.nhirakawa.swarm.protocol.config;

public enum ConfigPath {
  SWARM_PROTOCOL_PERIOD("swarm.protocol.period"),
  SWARM_MESSAGE_TIMEOUT("swarm.protocol.messageTimeout"),
  SWARM_PROTOCOL_TICK("swarm.protocol.tick"),
  SWARM_FAILURE_SUBGROUP("swarm.protocol.failureSubGroup"),
  CLUSTER_NODES("swarm.clusterNodes"),
  LOCAL_NODE("swarm.localNode"),
  DEBUG_ENABLED("swarm.debug.enabled"),
  FAILURE_INJECTION_PERCENT("swarm.debug.failureInjectionPercent"),
  SWARM_STATE_BUFFER_SIZE("swarm.stateBufferSize");
  private final String configPath;

  ConfigPath(String configPath) {
    this.configPath = configPath;
  }

  public String getConfigPath() {
    return configPath;
  }
}
