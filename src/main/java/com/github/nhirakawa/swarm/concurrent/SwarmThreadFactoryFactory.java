package com.github.nhirakawa.swarm.concurrent;

import java.util.concurrent.ThreadFactory;

import com.github.nhirakawa.swarm.config.SwarmNode;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public final class SwarmThreadFactoryFactory {

  private SwarmThreadFactoryFactory() {}

  public static ThreadFactory forNode(String threadName, SwarmNode swarmNode) {
    return new ThreadFactoryBuilder()
        .setNameFormat(String.format("%s-%s-%s-%%s", threadName, swarmNode.getHost(), swarmNode.getPort()))
        .setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
        .build();
  }

}
