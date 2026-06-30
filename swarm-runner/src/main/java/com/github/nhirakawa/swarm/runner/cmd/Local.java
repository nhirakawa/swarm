package com.github.nhirakawa.swarm.runner.cmd;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import com.github.nhirakawa.swarm.runner.Banner;
import com.github.nhirakawa.swarm.runner.admin.AdminConfig;
import com.github.nhirakawa.swarm.runner.admin.AdminService;
import com.github.nhirakawa.swarm.runner.factory.SwarmServiceFactory;
import com.github.nhirakawa.swarm.runner.json.Json;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.ServiceObserver;
import com.github.nhirakawa.swarm.runner.service.SwarmServiceRegistry;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "local")
public class Local implements Callable<Integer> {

	private static final Logger LOG = LogManager.getLogger(Local.class);

	@CommandLine.Option(
		names = { "--config" },
		paramLabel = "CONFIG",
		description = "The path to the config file",
		required = true
	)
	private String configFilePath;

	private final NetworkSimulator networkSimulator;
	private final SwarmServiceFactory swarmServiceFactory;
	private final Jinjava jinjava;

	@Inject
	Local(
		NetworkSimulator networkSimulator,
		SwarmServiceFactory swarmServiceFactory,
		Jinjava jinjava
	) {
		this.networkSimulator = networkSimulator;
		this.swarmServiceFactory = swarmServiceFactory;
		this.jinjava = jinjava;
	}

	@Override
	public Integer call() throws Exception {
		LOG.info("\n{}\n", Banner.getOrDefault("swarm-local.txt", "swarm-local"));
		var config = readConfigFile();

		SwarmServiceRegistry registry = createServices(config);

		if (registry.size() == 0) {
			return 0;
		}

		for (SwarmService swarmService : registry.toList()) {
			swarmService.startAsync();
		}

		Optional<AdminService> adminService = createAndStartAdminService(
			config.getAdminConfig(),
			registry,
			config
		);

		ServiceObserver serviceObserver = new ServiceObserver(
			registry,
			adminService
		);

		serviceObserver.startAsync().awaitRunning(Duration.ofSeconds(5));

		Runtime.getRuntime().addShutdownHook(
			new Thread(serviceObserver::stopAsync)
		);

		serviceObserver.awaitTerminated();

		return 0;
	}

	private LocalSwarmConfig readConfigFile() throws IOException {
		Preconditions.checkNotNull(configFilePath);
		if (configFilePath.startsWith("classpath:")) {
			return readConfigFileFromClasspath(configFilePath.substring(10));
		} else if (configFilePath.startsWith("file:")) {
			return readConfigFileFromFileSystem(configFilePath.substring(5));
		} else {
			return readConfigFileFromFileSystem(configFilePath);
		}
	}

	private LocalSwarmConfig readConfigFileFromClasspath(String resourcesPath)
		throws IOException {
		try (
			InputStream inputStream = Resources.getResource(
				resourcesPath
			).openStream()
		) {
			return Json.reader().readValue(inputStream, LocalSwarmConfig.class);
		}
	}

	private LocalSwarmConfig readConfigFileFromFileSystem(String configFilePath)
		throws IOException {
		try (
			InputStream inputStream = Files.newInputStream(
				Paths.get(configFilePath),
				StandardOpenOption.READ
			)
		) {
			return Json.reader().readValue(inputStream, LocalSwarmConfig.class);
		}
	}

	private SwarmServiceRegistry createServices(LocalSwarmConfig config) {
		networkSimulator.startAsync().awaitRunning();

		SwarmServiceRegistry registry = new SwarmServiceRegistry();
		generateSwarmAddresses(config.getNumberOfNodes())
			.stream()
			.map(address -> swarmServiceFactory.create(address, config))
			.forEach(registry::add);
		return registry;
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private Optional<AdminService> createAndStartAdminService(
		Optional<AdminConfig> config,
		SwarmServiceRegistry registry,
		LocalSwarmConfig localSwarmConfig
	) throws TimeoutException {
		if (config.isEmpty()) {
			return Optional.empty();
		}

		AdminService adminService = new AdminService(
			config.get(),
			jinjava,
			registry::getSnapshots,
			swarmServiceFactory,
			registry,
			localSwarmConfig
		);
		adminService.startAsync().awaitRunning(Duration.ofSeconds(10));
		return Optional.of(adminService);
	}

	private static List<SwarmAddress> generateSwarmAddresses(int count) {
		List<SwarmAddress> swarmAddresses = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			String host = "127.0.0.1";
			int port = 1000 * (i + 1);
			SwarmAddress swarmAddress = new InMemorySwarmAddress(
				"%s-%d".formatted(host, port)
			);
			swarmAddresses.add(swarmAddress);
		}

		return swarmAddresses;
	}
}
