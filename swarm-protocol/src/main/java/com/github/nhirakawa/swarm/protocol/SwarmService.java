package com.github.nhirakawa.swarm.protocol;

import com.github.nhirakawa.swarm.protocol.state.SwarmStateMachine;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.google.common.util.concurrent.AbstractIdleService;
import java.time.Duration;

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
    swarmTransport.startAsync();
    stateMachine.startAsync();

    swarmTransport.awaitRunning(Duration.ofSeconds(5));
    stateMachine.awaitRunning(Duration.ofSeconds(5));
  }

  @Override
  protected void shutDown() throws Exception {
    swarmTransport.stopAsync();
    stateMachine.stopAsync();

    swarmTransport.awaitTerminated(Duration.ofSeconds(5));
    stateMachine.awaitTerminated(Duration.ofSeconds(5));
  }

  public String getName() {
    return stateMachine.getName();
  }
}
