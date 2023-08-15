package com.github.nhirakawa.swarm.protocol.protocol;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.util.Fakes;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SwarmStateMachineTest {
  private static final SwarmConfig SWARM_CONFIG = SwarmConfig
    .builder()
    .setFailureSubGroup(1)
    .setLocalNode(
      SwarmNode.builder().setHost("localhost").setPort(8080).build()
    )
    .addClusterNodes(
      SwarmNode.builder().setHost("localhost").setPort(8081).build()
    )
    .setProtocolPeriod(Duration.ofSeconds(1))
    .setMessageTimeout(Duration.ofMillis(10))
    .setProtocolTick(Duration.ofMillis(1))
    .setSwarmStateBufferSize(10)
    .setDebugEnabled(false)
    .setFailureInjectionPercent(0)
    .build();

  @Mock
  private SwarmFailureInjector swarmFailureInjector;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(swarmFailureInjector.shouldInjectFailure()).thenReturn(false);
  }

  @Test
  public void itSendsPingRequest() {
    SwarmStateMachine swarmStateMachine = new SwarmStateMachine(
      SWARM_CONFIG,
      Fakes.clock(),
      Fakes.eventBus(),
      swarmFailureInjector
    );
  }
}
