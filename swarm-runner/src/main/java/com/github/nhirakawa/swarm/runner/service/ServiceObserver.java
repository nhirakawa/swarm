package com.github.nhirakawa.swarm.runner.service;

import com.github.nhirakawa.swarm.protocol.SwarmService;
import com.github.nhirakawa.swarm.runner.admin.AdminService;
import com.google.common.util.concurrent.AbstractScheduledService;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServiceObserver extends AbstractScheduledService {

	private static final Logger LOG = LogManager.getLogger(ServiceObserver.class);

	private static final Set<State> TERMINAL_STATES = EnumSet.of(
		State.TERMINATED,
		State.FAILED
	);

	private final SwarmServiceRegistry registry;

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private final Optional<AdminService> adminService;

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public ServiceObserver(
		SwarmServiceRegistry registry,
		Optional<AdminService> adminService
	) {
		this.registry = registry;
		this.adminService = adminService;
	}

	@Override
	protected void runOneIteration() throws Exception {
		for (SwarmService service : registry.toList()) {
			if (TERMINAL_STATES.contains(service.state())) {
				LOG.info("Service {} has state {}", service.getName(), service.state());
				stopAsync();
			}
		}

		if (
			adminService.isPresent() &&
			TERMINAL_STATES.contains(adminService.get().state())
		) {
			LOG.info("AdminService has state {}", adminService.get().state());
			stopAsync();
		}
	}

	@Override
	protected Scheduler scheduler() {
		return Scheduler.newFixedDelaySchedule(
			Duration.ofSeconds(1),
			Duration.ofMillis(100)
		);
	}
}
