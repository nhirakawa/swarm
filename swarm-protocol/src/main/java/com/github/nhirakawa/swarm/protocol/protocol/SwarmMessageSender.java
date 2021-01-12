package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import java.util.concurrent.CompletableFuture;

public interface SwarmMessageSender {
  CompletableFuture<?> send(BaseSwarmMessage swarmEnvelope);
}
