package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;

public interface SwarmMessageSender {
  void send(BaseSwarmMessage swarmEnvelope);
}
