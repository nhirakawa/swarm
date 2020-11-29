package com.github.nhirakawa.swarm.protocol.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.config.SwarmNodeModel;
import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.timeout.EmptyTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.PingTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.util.SwarmStateBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

public class SwarmProtocolTest {
  private static final SwarmNode LOCAL_NODE = SwarmNode
    .builder()
    .setHost("localhost")
    .setPort(8000)
    .setUniqueId(UUID.randomUUID())
    .build();

  private static final SwarmNode OTHER_NODE = SwarmNode
    .builder()
    .setHost("localhost")
    .setPort(8001)
    .setUniqueId(UUID.randomUUID())
    .build();

  private static final SwarmConfig BASE = SwarmConfig
    .builder()
    .setDebugEnabled(false)
    .setFailureInjectionPercent(0)
    .setFailureSubGroup(1)
    .addClusterNodes(OTHER_NODE)
    .setLocalNode(LOCAL_NODE)
    .setEntireClusterLocal(false)
    .setProtocolPeriod(Duration.ofSeconds(1))
    .setMessageTimeout(Duration.ofMillis(10))
    .setSwarmStateBufferSize(10)
    .setProtocolTick(Duration.ofMillis(1))
    .build();

  @Test
  public void itNoOpsOnMessageTimeout() {
    Clock clock = new FakeClock();

    Instant start = clock.instant();

    SwarmState initialSwarmState = SwarmState
      .builder()
      .setTimestamp(start)
      .setLastProtocolPeriodStarted(start)
      .setLastProtocolPeriodId("asdf")
      .build();

    SwarmProtocol swarmProtocol = new SwarmProtocol(
      BASE,
      new SwarmStateBuffer(initialSwarmState, 10),
      clock
    );

    SwarmTimeoutMessage swarmTimeoutMessage = SwarmTimeoutMessage
      .builder()
      .setTimestamp(start.plusMillis(2))
      .build();

    TimeoutResponse timeoutResponse = swarmProtocol.handle(swarmTimeoutMessage);

    assertThat(timeoutResponse).isInstanceOf(EmptyTimeoutResponse.class);
  }

  @Test
  public void itReturnsPingWhenNewProtocolStarts() {
    Clock clock = new FakeClock();

    Instant start = clock.instant();

    SwarmState initialSwarmState = SwarmState
      .builder()
      .setTimestamp(start)
      .setLastProtocolPeriodStarted(start)
      .setLastProtocolPeriodId("asdf")
      .build();

    SwarmProtocol swarmProtocol = new SwarmProtocol(
      BASE,
      new SwarmStateBuffer(initialSwarmState, 10),
      clock
    );

    SwarmTimeoutMessage timeoutMessage = SwarmTimeoutMessage
      .builder()
      .setTimestamp(start.plusSeconds(100))
      .build();

    TimeoutResponse timeoutResponse = swarmProtocol.handle(timeoutMessage);

    assertThat(timeoutResponse).isInstanceOf(PingTimeoutResponse.class);
  }
}
