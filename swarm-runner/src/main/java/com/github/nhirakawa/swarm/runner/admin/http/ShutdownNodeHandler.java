package com.github.nhirakawa.swarm.runner.admin.http;

import com.github.nhirakawa.swarm.runner.service.SwarmServiceRegistry;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import javax.annotation.Nonnull;

public class ShutdownNodeHandler implements Handler {

	private final SwarmServiceRegistry registry;

	public ShutdownNodeHandler(SwarmServiceRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void handle(@Nonnull Context context) throws Exception {
		String address = context.pathParam("address");
		registry.remove(address);
		context.status(204);
	}
}
