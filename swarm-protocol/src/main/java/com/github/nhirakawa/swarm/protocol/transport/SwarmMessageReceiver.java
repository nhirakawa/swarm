package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;
import java.util.Optional;

public interface SwarmMessageReceiver {
	Optional<StateMachineMessage> receive(Duration timeout);
}
