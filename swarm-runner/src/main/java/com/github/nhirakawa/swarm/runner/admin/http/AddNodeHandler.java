package com.github.nhirakawa.swarm.runner.admin.http;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.github.nhirakawa.swarm.runner.factory.SwarmServiceFactory;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.SwarmServiceRegistry;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import javax.annotation.Nonnull;

public class AddNodeHandler implements Handler {

	private final SwarmServiceFactory swarmServiceFactory;
	private final SwarmServiceRegistry registry;
	private final LocalSwarmConfig localSwarmConfig;

	public AddNodeHandler(
		SwarmServiceFactory swarmServiceFactory,
		SwarmServiceRegistry registry,
		LocalSwarmConfig localSwarmConfig
	) {
		this.swarmServiceFactory = swarmServiceFactory;
		this.registry = registry;
		this.localSwarmConfig = localSwarmConfig;
	}

	@Override
	public void handle(@Nonnull Context context) throws Exception {
		int port = 1000 * (registry.size() + 1);
		SwarmAddress address = new InMemorySwarmAddress(
			"127.0.0.1-%d".formatted(port)
		);

		SwarmService service = swarmServiceFactory.create(
			address,
			localSwarmConfig
		);
		service.startAsync();
		registry.add(service);

		context.status(204);
	}
}
