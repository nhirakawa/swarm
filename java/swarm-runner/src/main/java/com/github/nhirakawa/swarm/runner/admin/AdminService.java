package com.github.nhirakawa.swarm.runner.admin;

import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.runner.admin.http.AddNodeHandler;
import com.github.nhirakawa.swarm.runner.admin.http.ClusterStateHandler;
import com.github.nhirakawa.swarm.runner.admin.http.ContainerHandler;
import com.github.nhirakawa.swarm.runner.admin.http.HealthHandler;
import com.github.nhirakawa.swarm.runner.admin.http.ShutdownNodeHandler;
import com.github.nhirakawa.swarm.runner.factory.SwarmServiceFactory;
import com.github.nhirakawa.swarm.runner.model.NodeConfigModel;
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
	private final Javalin app;

	private AdminService(AdminConfig config, Javalin app) {
		this.config = config;
		this.app = app;
	}

	public static AdminService forSimulation(
		AdminConfig config,
		Jinjava jinjava,
		Supplier<List<StateSnapshot>> snapshotListSupplier,
		SwarmServiceFactory swarmServiceFactory,
		SwarmServiceRegistry registry,
		NodeConfigModel nodeConfig
	) {
		Javalin app = buildBaseApp()
			.get(
				"/app/container",
				new ContainerHandler(jinjava, snapshotListSupplier)
			)
			.post(
				"/app/nodes",
				new AddNodeHandler(swarmServiceFactory, registry, nodeConfig)
			)
			.delete("/app/nodes/{address}", new ShutdownNodeHandler(registry));
		return new AdminService(config, app);
	}

	public static AdminService forLocal(
		AdminConfig config,
		Supplier<StateSnapshot> snapshotSupplier
	) {
		Javalin app = buildBaseApp()
			.get("/health", new HealthHandler(snapshotSupplier))
			.get("/status", new ClusterStateHandler(snapshotSupplier));
		return new AdminService(config, app);
	}

	@Override
	protected void startUp() throws Exception {
		app.start(config.getPort());
	}

	@Override
	protected void shutDown() throws Exception {
		app.stop();
	}

	private static Javalin buildBaseApp() {
		return Javalin.create(AdminService::customize);
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
