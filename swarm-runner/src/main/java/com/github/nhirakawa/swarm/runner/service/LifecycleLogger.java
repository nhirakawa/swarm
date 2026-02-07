package com.github.nhirakawa.swarm.runner.service;

import com.google.common.util.concurrent.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LifecycleLogger extends Service.Listener {

	private final Logger log;

	public LifecycleLogger(Service service) {
		log = LogManager.getLogger("%s-listener".formatted(service.getClass().getSimpleName()));
	}

	@Override
	public void starting() {
		log.info("Starting");
	}

	@Override
	public void running() {
		log.info("Running");
	}

	@Override
	public void stopping(Service.State from) {
		log.info("Stopping source {}", from);
	}

	@Override
	public void terminated(Service.State from) {
		log.info("Terminated source {}", from);
	}

	@Override
	public void failed(Service.State from, Throwable failure) {
		log.error("Failed source {}", from, failure);
	}
}
