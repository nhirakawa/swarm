package com.github.nhirakawa.swarm.runner.guice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransportRegistry;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulationConfig;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import com.github.nhirakawa.swarm.protocol.transport.mem.PerfectNetworkSimulationConfig;
import com.github.nhirakawa.swarm.runner.json.Json;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.hubspot.jinjava.Jinjava;

public class LocalSwarmModule extends AbstractModule {

	@Provides
	Jinjava provideJinjava() {
		return new Jinjava();
	}

	@Provides
	NetworkSimulationConfig provideNetworkSimulationConfig() {
		return new PerfectNetworkSimulationConfig();
	}

	@Provides
	@Singleton
	InMemoryTransportRegistry provideInMemoryTransportRegistry() {
		return new InMemoryTransportRegistry();
	}

	@Provides
	@Singleton
	NetworkSimulator provideNetworkSimulator(InMemoryTransportRegistry registry, NetworkSimulationConfig networkSimulationConfig) {
		return new NetworkSimulator(registry, networkSimulationConfig);
	}

	@Provides
	@Singleton
	ObjectMapper provideObjectMapper() {
		return Json.buildForLocal();
	}

	@Provides
	@Singleton
	ObjectReader provideObjectReader() {
		return Json.reader();
	}

	@Provides
	@Singleton
	ObjectWriter provideObjectWriter() {
		return Json.writer();
	}

}
