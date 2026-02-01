package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineResponse;

public interface SwarmMessageSender {
  void send(StateMachineResponse response);
}
