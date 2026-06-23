package com.github.nhirakawa.swarm.runner.cmd;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.NetworkSimulator;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.runner.Banner;
import com.github.nhirakawa.swarm.runner.admin.AdminConfig;
import com.github.nhirakawa.swarm.runner.admin.AdminService;
import com.github.nhirakawa.swarm.runner.factory.SwarmServiceFactory;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.ServiceObserver;
import com.google.common.base.Preconditions;
import com.hubspot.jinjava.Jinjava;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@CommandLine.Command(name = "local")
public class Local implements Callable<Integer> {

	private static final Logger LOG = LogManager.getLogger(Local.class);

	@CommandLine.Option(names = {"--config"}, paramLabel = "CONFIG", description = "The path to the config file", required = true)
	private Path configFilePath;

	private final NetworkSimulator networkSimulator;
	private final SwarmServiceFactory swarmServiceFactory;
	private final Jinjava jinjava;

	@Inject
	Local(NetworkSimulator networkSimulator, SwarmServiceFactory swarmServiceFactory, Jinjava jinjava) {
		this.networkSimulator = networkSimulator;
		this.swarmServiceFactory = swarmServiceFactory;
		this.jinjava = jinjava;
	}

	@Override
	public Integer call() throws Exception {
		LOG.info("\n{}\n", Banner.getOrDefault("swarm-local.txt", "swarm-local"));
		var config = readConfigFile();

		List<SwarmService> swarmServices = createServices(config);

		if (swarmServices.isEmpty()) {
			return 0;
		}

		for (SwarmService swarmService : swarmServices) {
			swarmService.startAsync();
		}

		Supplier<List<StateSnapshot>> snapshotListSupplier = () -> swarmServices.stream().map(SwarmService::getSnapshot).toList();
		Optional<AdminService> adminService = createAndStartAdminService(config.getAdminConfig(), snapshotListSupplier);

		ServiceObserver serviceObserver = new ServiceObserver(swarmServices, adminService);

		serviceObserver.startAsync().awaitRunning(Duration.ofSeconds(5));

		Runtime.getRuntime().addShutdownHook(new Thread(serviceObserver::stopAsync));

		serviceObserver.awaitTerminated();

		return 0;
	}

	private LocalSwarmConfig readConfigFile() throws IOException {
		Preconditions.checkNotNull(configFilePath);
		try (InputStream inputStream = Files.newInputStream(configFilePath, StandardOpenOption.READ)) {
			return ObjectMapperWrapper.instance().reader().readValue(inputStream, LocalSwarmConfig.class);
		}
	}

	private List<SwarmService> createServices(LocalSwarmConfig config) {
		networkSimulator.startAsync().awaitRunning();

		return generateSwarmAddresses(config.getNumberOfNodes()).stream()
				.map(address -> swarmServiceFactory.create(address, config))
				.toList();
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private Optional<AdminService> createAndStartAdminService(Optional<AdminConfig> config, Supplier<List<StateSnapshot>> snapshotListSupplier) throws TimeoutException {
		if (config.isEmpty()) {
			return Optional.empty();
		}

		AdminService adminService = new AdminService(config.get(), jinjava, snapshotListSupplier);
		adminService.startAsync().awaitRunning(Duration.ofSeconds(10));
		return Optional.of(adminService);
	}

	private static List<SwarmAddress> generateSwarmAddresses(int count) {
		List<SwarmAddress> swarmAddresses = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			String host = "127.0.0.1";
			int port = 1000 * (i + 1);
			SwarmAddress swarmAddress = new InMemorySwarmAddress("%s-%d".formatted(host, port));
			swarmAddresses.add(swarmAddress);
		}

		return swarmAddresses;
	}
}
