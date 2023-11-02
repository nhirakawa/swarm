package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.guice.SwarmNettyModule;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.guice.SwarmConfigModule;
import com.github.nhirakawa.swarm.protocol.guice.SwarmProtocolModule;
import com.github.nhirakawa.swarm.runner.util.NamedService;
import com.github.nhirakawa.swarm.runner.util.ServiceObserver;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "local")
public class SwarmLocalClusterRunner implements Callable<Integer> {

  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmLocalClusterRunner.class
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

  @CommandLine.Option(
    names = "--failure-subgroup",
    defaultValue = "1",
    showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  private int failureSubGroup;

  @CommandLine.Option(names = "--node", arity = "1..*", required = true)
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
      BannerUtil.getOrDefault("swarm-local-banner.txt", "swarm-local-cluster")
    );

    List<NamedService> services = new ArrayList<>();

    for (SwarmNode swarmNode : clusterNodes) {
      SwarmConfig swarmConfig = SwarmConfig
        .builder()
        .setProtocolPeriod(protocolPeriod)
        .setMessageTimeout(messageTimeout)
        .setProtocolTick(tick)
        .setFailureSubGroup(failureSubGroup)
        .setLocalNode(swarmNode)
        .addAllClusterNodes(clusterNodes)
        .setSwarmStateBufferSize(stateBufferSize)
        .setDebugEnabled(false)
        .setFailureInjectionPercent(0)
        .build();

      SwarmConfigModule swarmConfigModule = new SwarmConfigModule(swarmConfig);

      Injector injector = Guice.createInjector(
        swarmConfigModule,
        new SwarmProtocolModule(),
        new SwarmNettyModule()
      );

      SwarmService swarmService = injector.getInstance(SwarmService.class);

      swarmService.startAsync().awaitRunning(Duration.ofSeconds(1));

      services.add(swarmService);
    }

    ServiceObserver serviceObserver = new ServiceObserver(services);

    serviceObserver.startAsync().awaitRunning(Duration.ofSeconds(1));

    serviceObserver.awaitTerminated();

    LOG.info("ServiceObserver is terminated");

    return 0;
  }
}
