package com.github.nhirakawa.swarm.runner.cmd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.protocol.SwarmTerminationCallback;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.stdio.StdioAddress;
import com.github.nhirakawa.swarm.protocol.transport.stdio.StdioTransport;
import com.github.nhirakawa.swarm.runner.admin.AdminConfig;
import com.github.nhirakawa.swarm.runner.admin.AdminService;
import com.github.nhirakawa.swarm.runner.factory.SwarmStateMachineFactory;
import com.github.nhirakawa.swarm.runner.json.Json;
import com.github.nhirakawa.swarm.runner.model.NodeConfig;
import com.github.nhirakawa.swarm.runner.service.LifecycleLogger;
import com.github.nhirakawa.swarm.runner.service.ServiceObserver;
import com.github.nhirakawa.swarm.runner.service.SwarmServiceRegistry;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "local")
public class Local implements Callable<Integer> {

	private static final Logger LOG = LogManager.getLogger(Local.class);

	@CommandLine.Option(
		names = { "--address" },
		paramLabel = "ADDRESS",
		description = "The address of this node",
		required = true
	)
	private String address;

	@CommandLine.Option(
		names = { "--protocol-period" },
		paramLabel = "DURATION",
		description = "Protocol period (ISO-8601, e.g. PT10S)",
		required = true
	)
	private Duration protocolPeriod;

	@CommandLine.Option(
		names = { "--message-timeout" },
		paramLabel = "DURATION",
		description = "Message timeout (ISO-8601, e.g. PT1S); must be less than protocol period",
		required = true
	)
	private Duration messageTimeout;

	@CommandLine.Option(
		names = { "--protocol-tick" },
		paramLabel = "DURATION",
		description = "Protocol tick interval (ISO-8601, e.g. PT0.05S)",
		required = true
	)
	private Duration protocolTick;

	@CommandLine.Option(
		names = { "--failure-sub-group" },
		paramLabel = "N",
		description = "Failure sub-group size",
		required = true
	)
	private int failureSubGroup;

	@CommandLine.Option(
		names = { "--admin-port" },
		paramLabel = "PORT",
		description = "Port for the admin HTTP server (omit to disable)"
	)
	private Integer adminPort;

	@CommandLine.Option(
		names = { "--peer" },
		paramLabel = "ADDRESS",
		description = "Address of a peer node; may be specified multiple times"
	)
	private String[] peers = new String[0];

	private final SwarmStateMachineFactory stateMachineFactory;

	@Inject
	Local(SwarmStateMachineFactory stateMachineFactory) {
		this.stateMachineFactory = stateMachineFactory;
	}

	@Override
	public Integer call() throws Exception {
		NodeConfig config = NodeConfig.builder()
			.setProtocolPeriod(protocolPeriod)
			.setMessageTimeout(messageTimeout)
			.setProtocolTick(protocolTick)
			.setFailureSubGroup(failureSubGroup)
			.setAdminConfig(
				Optional.ofNullable(adminPort).map(p ->
					AdminConfig.builder().setPort(p).build()
				)
			)
			.build();
		StdioAddress localAddress = new StdioAddress(address);

		ObjectMapper objectMapper = Json.buildForStdio();
		StdioTransport transport = new StdioTransport(
			objectMapper.writer(),
			objectMapper.reader()
		);

		Set<StdioAddress> initialGroup = Arrays.stream(peers)
			.map(StdioAddress::new)
			.collect(Collectors.toSet());

		SwarmConfig swarmConfig = SwarmConfig.builder()
			.setLocalAddress(localAddress)
			.setMulticastAddress(transport.getMulticastAddress())
			.setProtocolPeriod(config.getProtocolPeriod())
			.setMessageTimeout(config.getMessageTimeout())
			.setProtocolTick(config.getProtocolTick())
			.setFailureSubGroup(config.getFailureSubGroup())
			.setProtocolPeriodJitter(config.getProtocolPeriodJitter())
			.setMessageTimeoutJitter(config.getMessageTimeoutJitter())
			.setInitialGroup(initialGroup)
			.build();

		AtomicReference<SwarmService> serviceRef = new AtomicReference<>();
		SwarmTerminationCallback callback = () -> {
			SwarmService s = serviceRef.get();
			if (s != null) {
				s.stopAsync();
			}
		};

		SwarmStateMachine stateMachine = stateMachineFactory.create(
			swarmConfig,
			transport.receiver(),
			transport.sender(),
			callback
		);

		SwarmService swarmService = new SwarmService(stateMachine, transport);
		serviceRef.set(swarmService);
		swarmService.addListener(
			new LifecycleLogger(swarmService),
			MoreExecutors.directExecutor()
		);

		SwarmServiceRegistry registry = new SwarmServiceRegistry();
		registry.add(swarmService);

		swarmService.startAsync();
		LOG.info("Swarm node {} started", address);

		Optional<AdminService> adminService = createAndStartAdminService(
			config,
			swarmService::getSnapshot
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

	private Optional<AdminService> createAndStartAdminService(
		NodeConfig config,
		Supplier<StateSnapshot> snapshotSupplier
	) throws TimeoutException {
		if (config.getAdminConfig().isEmpty()) {
			return Optional.empty();
		}

		AdminService adminService = AdminService.forLocal(
			config.getAdminConfig().get(),
			snapshotSupplier
		);
		adminService.startAsync().awaitRunning(Duration.ofSeconds(10));
		return Optional.of(adminService);
	}
}
