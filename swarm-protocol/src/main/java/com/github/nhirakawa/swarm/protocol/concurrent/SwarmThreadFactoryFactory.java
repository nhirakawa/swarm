package com.github.nhirakawa.swarm.protocol.concurrent;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;

public final class SwarmThreadFactoryFactory {

  private SwarmThreadFactoryFactory() {}

  public static ThreadFactory forNode(String threadName, SwarmNode swarmNode) {
    return new ThreadFactoryBuilder()
      .setNameFormat(
        String.format(
          "%s-%s-%s-%%s",
          threadName,
          swarmNode.getHost(),
          swarmNode.getPort()
        )
      )
      .setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
      .build();
  }
}
