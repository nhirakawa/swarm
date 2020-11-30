package com.github.nhirakawa.swarm.protocol.config;

public enum ConfigPath {
  SWARM_PROTOCOL_PERIOD(
    "swarm.com.github.nhirakawa.swarm.protocol.protocol.period"
  ),
  SWARM_MESSAGE_TIMEOUT(
    "swarm.com.github.nhirakawa.swarm.protocol.protocol.messageTimeout"
  ),
  SWARM_PROTOCOL_TICK(
    "swarm.com.github.nhirakawa.swarm.protocol.protocol.tick"
  ),
  SWARM_FAILURE_SUBGROUP(
    "swarm.com.github.nhirakawa.swarm.protocol.protocol.failureSubGroup"
  ),
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
