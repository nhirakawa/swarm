package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;

public interface SwarmMessageSender {
	void send(StateMachineMessage message, Duration timeout);
}
