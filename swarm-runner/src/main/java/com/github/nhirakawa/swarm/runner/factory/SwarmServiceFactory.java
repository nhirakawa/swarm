package com.github.nhirakawa.swarm.runner.factory;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.SwarmTerminationCallback;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.LifecycleLogger;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicReference;

public class SwarmServiceFactory {

	private final SwarmTransportFactory transportFactory;
	private final SwarmStateMachineFactory stateMachineFactory;

	@Inject
	public SwarmServiceFactory(
		SwarmTransportFactory transportFactory,
		SwarmStateMachineFactory stateMachineFactory
	) {
		this.transportFactory = transportFactory;
		this.stateMachineFactory = stateMachineFactory;
	}

	public SwarmService create(
		SwarmAddress address,
		LocalSwarmConfig localSwarmConfig
	) {
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

		AtomicReference<SwarmService> serviceRef = new AtomicReference<>();
		SwarmTerminationCallback callback = () -> {
			SwarmService s = serviceRef.get();
			if (s != null) {
				s.stopAsync();
			}
		};

		SwarmStateMachine stateMachine = stateMachineFactory.create(
			swarmConfig,
			transport.receiver(),
			transport.sender(),
			callback
		);

		SwarmService swarmService = new SwarmService(stateMachine, transport);
		serviceRef.set(swarmService);
		swarmService.addListener(
			new LifecycleLogger(swarmService),
			MoreExecutors.directExecutor()
		);
		return swarmService;
	}
}
