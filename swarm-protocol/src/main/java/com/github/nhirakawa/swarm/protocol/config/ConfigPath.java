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
  RUN_ENTIRE_CLUSTER_LOCALLY("swarm.runEntireClusterLocally"),
  SWARM_STATE_BUFFER_SIZE("swarm.stateBufferSize");
  private final String configPath;

  ConfigPath(String configPath) {
    this.configPath = configPath;
  }

  public String getConfigPath() {
    return configPath;
  }
}
