package com.github.nhirakawa.swarm.runner.factory;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransportRegistry;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import jakarta.inject.Inject;

public class SwarmTransportFactory {

	private final InMemoryTransportRegistry registry;
	private final ObjectWriter objectWriter;
	private final ObjectReader objectReader;
	private final NetworkSimulator networkSimulator;

	@Inject
	public SwarmTransportFactory(
		InMemoryTransportRegistry registry,
		NetworkSimulator networkSimulator,
		ObjectWriter objectWriter,
		ObjectReader objectReader
	) {
		this.registry = registry;
		this.objectWriter = objectWriter;
		this.objectReader = objectReader;
		this.networkSimulator = networkSimulator;
	}

	public SwarmTransport create(SwarmAddress address) {
		return new InMemoryTransport(
			address,
			registry,
			objectWriter,
			objectReader,
			networkSimulator
		);
	}
}
