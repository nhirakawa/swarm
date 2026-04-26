package com.github.nhirakawa.swarm.runner.factory;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransportRegistry;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import jakarta.inject.Inject;

public class SwarmTransportFactory {

	private final InMemoryTransportRegistry registry;
	private final NetworkSimulator networkSimulator;

	@Inject
	public SwarmTransportFactory(InMemoryTransportRegistry registry, NetworkSimulator networkSimulator) {
		this.registry = registry;
		this.networkSimulator = networkSimulator;
	}

	public SwarmTransport create(SwarmAddress address) {
		return new InMemoryTransport(address, registry, networkSimulator);
	}
}
