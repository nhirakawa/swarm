package com.github.nhirakawa.swarm.runner.guice;

import com.github.nhirakawa.swarm.protocol.transport.mem.DefaultNetworkSimulationConfig;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransportRegistry;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulationConfig;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.hubspot.jinjava.Jinjava;

public class LocalSwarmModule extends AbstractModule {

	@Provides
	Jinjava provideJinjava() {
		return new Jinjava();
	}

	@Provides
	NetworkSimulationConfig provideNetworkSimulationConfig() {
		return DefaultNetworkSimulationConfig.perfect();
	}

	@Provides
	InMemoryTransportRegistry provideInMemoryTransportRegistry() {
		return new InMemoryTransportRegistry();
	}

	@Provides
	NetworkSimulator provideNetworkSimulator(InMemoryTransportRegistry registry, NetworkSimulationConfig networkSimulationConfig) {
		return new NetworkSimulator(registry, networkSimulationConfig);
	}


}
