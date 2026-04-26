package com.github.nhirakawa.swarm.runner.factory;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.github.nhirakawa.swarm.runner.service.LifecycleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.inject.Inject;

public class SwarmStateMachineFactory {

	@Inject
	public SwarmStateMachineFactory() {}

	public SwarmStateMachine create(SwarmConfig config, SwarmMessageReceiver receiver, SwarmMessageSender sender) {
		SwarmStateMachine stateMachine = new SwarmStateMachine(config, receiver, sender);
		stateMachine.addListener(new LifecycleLogger(stateMachine), MoreExecutors.directExecutor());
		return stateMachine;
	}
}
