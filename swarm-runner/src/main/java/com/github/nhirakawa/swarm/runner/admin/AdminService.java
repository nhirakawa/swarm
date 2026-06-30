package com.github.nhirakawa.swarm.runner.admin;

import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.runner.admin.http.AddNodeHandler;
import com.github.nhirakawa.swarm.runner.admin.http.ContainerHandler;
import com.github.nhirakawa.swarm.runner.admin.http.ShutdownNodeHandler;
import com.github.nhirakawa.swarm.runner.factory.SwarmServiceFactory;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.SwarmServiceRegistry;
import com.google.common.util.concurrent.AbstractIdleService;
import com.hubspot.jinjava.Jinjava;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import java.util.List;
import java.util.function.Supplier;

public class AdminService extends AbstractIdleService {

	private final AdminConfig config;
	private final Jinjava jinjava;
	private final Supplier<List<StateSnapshot>> snapshotListSupplier;
	private final SwarmServiceFactory swarmServiceFactory;
	private final SwarmServiceRegistry registry;
	private final LocalSwarmConfig localSwarmConfig;
	private final Javalin app;

	public AdminService(
		AdminConfig config,
		Jinjava jinjava,
		Supplier<List<StateSnapshot>> snapshotListSupplier,
		SwarmServiceFactory swarmServiceFactory,
		SwarmServiceRegistry registry,
		LocalSwarmConfig localSwarmConfig
	) {
		this.config = config;
		this.jinjava = jinjava;
		this.snapshotListSupplier = snapshotListSupplier;
		this.swarmServiceFactory = swarmServiceFactory;
		this.registry = registry;
		this.localSwarmConfig = localSwarmConfig;
		this.app = createApp();
	}

	@Override
	protected void startUp() throws Exception {
		app.start(config.getPort());
	}

	@Override
	protected void shutDown() throws Exception {
		app.stop();
	}

	private Javalin createApp() {
		return Javalin.create(AdminService::customize)
			.get(
				"/app/container",
				new ContainerHandler(jinjava, snapshotListSupplier)
			)
			.post(
				"/app/nodes",
				new AddNodeHandler(swarmServiceFactory, registry, localSwarmConfig)
			)
			.delete(
				"/app/nodes/{address}",
				new ShutdownNodeHandler(registry)
			);
	}

	private static void customize(JavalinConfig config) {
		config.staticFiles.add(staticFileConfig -> {
			staticFileConfig.hostedPath = "/";
			staticFileConfig.directory = "/admin";
			staticFileConfig.location = Location.CLASSPATH;
		});

		config.http.gzipOnlyCompression();

		config.router.ignoreTrailingSlashes = true;
		config.router.treatMultipleSlashesAsSingleSlash = true;
		config.router.caseInsensitiveRoutes = true;
	}
}
