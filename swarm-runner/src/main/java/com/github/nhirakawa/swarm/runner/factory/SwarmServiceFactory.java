package com.github.nhirakawa.swarm.runner.factory;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.LifecycleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.inject.Inject;

public class SwarmServiceFactory {

	private final SwarmTransportFactory transportFactory;
	private final SwarmStateMachineFactory stateMachineFactory;

	@Inject
	public SwarmServiceFactory(SwarmTransportFactory transportFactory, SwarmStateMachineFactory stateMachineFactory) {
		this.transportFactory = transportFactory;
		this.stateMachineFactory = stateMachineFactory;
	}

	public SwarmService create(SwarmAddress address, LocalSwarmConfig localSwarmConfig) {
		SwarmTransport transport = transportFactory.create(address);

		SwarmConfig swarmConfig = SwarmConfig.builder()
				.setLocalAddress(address)
				.setMulticastAddress(transport.getMulticastAddress())
				.setProtocolPeriod(localSwarmConfig.getProtocolPeriod())
				.setMessageTimeout(localSwarmConfig.getMessageTimeout())
				.setProtocolTick(localSwarmConfig.getProtocolTick())
				.setFailureSubGroup(localSwarmConfig.getFailureSubGroup())
				.setProtocolPeriodJitter(localSwarmConfig.getProtocolPeriodJitter())
				.setMessageTimeoutJitter(localSwarmConfig.getMessageTimeoutJitter())
				.build();

		SwarmStateMachine stateMachine = stateMachineFactory.create(swarmConfig, transport.receiver(), transport.sender());

		SwarmService swarmService = new SwarmService(stateMachine, transport);
		swarmService.addListener(new LifecycleLogger(swarmService), MoreExecutors.directExecutor());
		return swarmService;
	}
}
