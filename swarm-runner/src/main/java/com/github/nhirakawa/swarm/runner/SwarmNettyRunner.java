package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.guice.SwarmNettyModule;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.guice.SwarmConfigModule;
import com.github.nhirakawa.swarm.protocol.guice.SwarmProtocolModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(
  name = "netty",
  description = "Run a single Swarm node using netty for transport"
)
public class SwarmNettyRunner implements Callable<Integer> {

  private static final Logger LOG = LogManager.getLogger(
    SwarmNettyRunner.class
  );

  @CommandLine.Option(
    names = "--protocol-period",
    defaultValue = "PT10S",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  private Duration protocolPeriod;

  @CommandLine.Option(
    names = "--message-timeout",
    defaultValue = "PT0.5S",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  private Duration messageTimeout;

  @CommandLine.Option(
    names = "--tick",
    defaultValue = "PT2S",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  private Duration tick;

  @CommandLine.Option(names = "--local-node", required = true)
  private SwarmNode localSwarmNode;

  @CommandLine.Option(names = "--node", arity = "1..*")
  private List<SwarmNode> clusterNodes;

  @CommandLine.Option(
    names = "--state-buffer-size",
    defaultValue = "10",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  private int stateBufferSize;

  public Integer call() throws Exception {
    LOG.info(
      "\n{}",
      BannerUtil.getOrDefault("swarm-netty-banner.txt", "swarm")
    );

    SwarmConfig swarmConfig = SwarmConfig
      .builder()
      .setProtocolPeriod(protocolPeriod)
      .setMessageTimeout(messageTimeout)
      .setProtocolTick(tick)
      .setLocalNode(localSwarmNode)
      .addAllClusterNodes(clusterNodes)
      .setSwarmStateBufferSize(stateBufferSize)
      .build();

    SwarmConfigModule swarmConfigModule = new SwarmConfigModule(swarmConfig);

    Injector injector = Guice.createInjector(
      swarmConfigModule,
      new SwarmNettyModule(),
      new SwarmProtocolModule()
    );

    SwarmService swarmService = injector.getInstance(SwarmService.class);

    swarmService.startAsync().awaitRunning(Duration.ofSeconds(1));

    swarmService.awaitTerminated();

    return 0;
  }
}
