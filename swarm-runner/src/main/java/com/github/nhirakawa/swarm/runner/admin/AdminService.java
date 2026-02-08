package com.github.nhirakawa.swarm.runner.admin;

import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.runner.admin.http.ContainerHandler;
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
	private final Javalin app;

	public AdminService(AdminConfig config, Jinjava jinjava, Supplier<List<StateSnapshot>> snapshotListSupplier) {
		this.config = config;
		this.jinjava = jinjava;
		this.snapshotListSupplier = snapshotListSupplier;
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
				.get("/app/container", new ContainerHandler(jinjava, snapshotListSupplier));
	}

	private static void customize(JavalinConfig config) {
		// Configure static file handling
		config.staticFiles.add(staticFileConfig ->
													 {staticFileConfig.hostedPath = "/";
														 staticFileConfig.directory = "/admin";
														 staticFileConfig.location = Location.CLASSPATH;
													 });

		// Configure HTTP
		config.http.gzipOnlyCompression();

		// Configure router
		config.router.ignoreTrailingSlashes = true;
		config.router.treatMultipleSlashesAsSingleSlash = true;
		config.router.caseInsensitiveRoutes = true;
	}
}
