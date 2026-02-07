package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;

public interface SwarmMessageSender {
  void send(StateMachineMessage message);
}
