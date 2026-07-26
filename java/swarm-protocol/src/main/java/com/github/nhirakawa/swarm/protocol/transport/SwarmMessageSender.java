package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public interface SwarmMessageSender {
	boolean send(StateMachineMessage message, Duration timeout)
		throws TimeoutException, InterruptedException;
}
