package com.github.nhirakawa.swarm.protocol.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingUncaughtExceptionHandler
  implements UncaughtExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(
    LoggingUncaughtExceptionHandler.class
  );

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    LOG.error("Uncaught exception", e);
  }
}
