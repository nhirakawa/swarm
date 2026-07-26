package com.github.nhirakawa.swarm.runner.admin.http;

import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

public class HealthHandler implements Handler {

	private final Supplier<StateSnapshot> snapshotSupplier;

	public HealthHandler(Supplier<StateSnapshot> snapshotSupplier) {
		this.snapshotSupplier = snapshotSupplier;
	}

	@Override
	public void handle(@Nonnull Context context) {
		if (snapshotSupplier.get() == null) {
			context.status(503).json(Map.of("status", "starting"));
		} else {
			context.status(200).json(Map.of("status", "ok"));
		}
	}
}
