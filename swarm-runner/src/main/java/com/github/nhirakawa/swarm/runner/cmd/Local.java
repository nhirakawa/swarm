package com.github.nhirakawa.swarm.runner.cmd;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransport;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemoryTransportRegistry;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.runner.BannerUtil;
import com.github.nhirakawa.swarm.runner.model.LocalSwarmConfig;
import com.github.nhirakawa.swarm.runner.service.LifecycleLogger;
import com.github.nhirakawa.swarm.runner.service.ServiceObserver;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "local")
public class Local implements Callable<Integer> {

	private static final Logger LOG = LogManager.getLogger(Local.class);

	@CommandLine.Option(names = {"--config"}, paramLabel = "CONFIG", description = "The path to the config file", required = true)
	Path configFilePath;

	Local() {}

	@Override
	public Integer call() throws Exception {
		LOG.info("\n{}\n", BannerUtil.getOrDefault("swarm-local.txt", "swarm-local"));
		var config = readConfigFile();

		List<SwarmService> services = createServices(config);

		if (services.isEmpty()) {
			return 0;
		}

		for (SwarmService swarmService : services) {
			swarmService.startAsync();
		}

		ServiceObserver serviceObserver = new ServiceObserver(services);

		serviceObserver.startAsync().awaitRunning(Duration.ofSeconds(5));

		Runtime.getRuntime().addShutdownHook(new Thread(serviceObserver::stopAsync));

		serviceObserver.awaitTerminated();

		return 0;
	}

	private LocalSwarmConfig readConfigFile() throws IOException {
		try (InputStream inputStream = Files.newInputStream(configFilePath, StandardOpenOption.READ)) {
			return ObjectMapperWrapper.instance().reader().readValue(inputStream, LocalSwarmConfig.class);
		}
	}

	private List<SwarmService> createServices(LocalSwarmConfig config) {
		InMemoryTransportRegistry registry = new InMemoryTransportRegistry();

		List<SwarmService> services = new ArrayList<>(config.getNumberOfNodes());

		List<SwarmAddress> swarmAddresses = generateSwarmAddresses(config.getNumberOfNodes());

		for (SwarmAddress swarmAddress : swarmAddresses) {
			SwarmTransport transport = new InMemoryTransport(swarmAddress, registry, 10);

			Set<SwarmAddress> restOfCluster = swarmAddresses.stream().filter(other -> !other.equals(swarmAddress)).collect(
					Collectors.toUnmodifiableSet());

			SwarmConfig swarmConfig = SwarmConfig.builder()
					.setLocalAddress(swarmAddress)
					.addAllInitialClusterMembership(restOfCluster)
					.setProtocolPeriod(config.getProtocolPeriod())
					.setMessageTimeout(config.getMessageTimeout())
					.setProtocolTick(config.getProtocolTick())
					.setFailureSubGroup(config.getFailureSubGroup())
					.setProtocolPeriodJitter(config.getProtocolPeriodJitter())
					.setMessageTimeoutJitter(config.getMessageTimeoutJitter())
					.build();

			SwarmStateMachine swarmStateMachine = new SwarmStateMachine(swarmConfig, transport.receiver(), transport.sender());
			swarmStateMachine.addListener(new LifecycleLogger(swarmStateMachine), MoreExecutors.directExecutor());

			SwarmService swarmService = new SwarmService(swarmStateMachine, transport);
			swarmService.addListener(new LifecycleLogger(swarmService), MoreExecutors.directExecutor());

			services.add(swarmService);
		}

		return services;
	}

	private static List<SwarmAddress> generateSwarmAddresses(int count) {
		List<SwarmAddress> swarmAddresses = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			String host = "127.0.0.1";
			int port = 1000 * (i + 1);
			SwarmAddress swarmAddress = new SwarmAddress(host, port, "%s-%d".formatted(host, port));
			swarmAddresses.add(swarmAddress);
		}

		return swarmAddresses;
	}
}
