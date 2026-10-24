package com.github.nhirakawa.swarm.protocol;

import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

public class SwarmService extends AbstractIdleService {

	private final SwarmStateMachine stateMachine;
	private final SwarmTransport swarmTransport;

	public SwarmService(
		SwarmStateMachine stateMachine,
		SwarmTransport swarmTransport
	) {
		this.stateMachine = stateMachine;
		this.swarmTransport = swarmTransport;
	}

	@Override
	protected void startUp() throws Exception {
		// If either part fails, stop the whole service so the failure is not hidden
		// behind a RUNNING state; shutDown then reports it.
		Listener stopOnFailure = new Listener() {
			@Override
			public void failed(State from, Throwable failure) {
				stopAsync();
			}
		};
		swarmTransport.addListener(stopOnFailure, MoreExecutors.directExecutor());
		stateMachine.addListener(stopOnFailure, MoreExecutors.directExecutor());

		swarmTransport.startAsync();
		stateMachine.startAsync();

		swarmTransport.awaitRunning(Duration.ofSeconds(5));
		stateMachine.awaitRunning(Duration.ofSeconds(5));
	}

	/** Fails the service if the state machine or transport failed. */
	@Override
	protected void shutDown() throws Exception {
		swarmTransport.stopAsync();
		stateMachine.stopAsync();

		awaitStopped(swarmTransport);
		awaitStopped(stateMachine);

		for (Service part : List.of(swarmTransport, stateMachine)) {
			if (part.state() == State.FAILED) {
				throw new IllegalStateException(
					part + " failed",
					part.failureCause()
				);
			}
		}
	}

	private static void awaitStopped(Service part) throws TimeoutException {
		try {
			part.awaitTerminated(Duration.ofSeconds(5));
		} catch (IllegalStateException e) {
			// Thrown when the part failed; checked above.
		}
	}

	public String getName() {
		return stateMachine.getName();
	}

	@Nullable
	public StateSnapshot getSnapshot() {
		return stateMachine.getSnapshot();
	}
}
