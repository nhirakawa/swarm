package com.github.nhirakawa.swarm.protocol.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingUncaughtExceptionHandler
  implements UncaughtExceptionHandler {

  private static final Logger LOG = LogManager.getLogger(
    LoggingUncaughtExceptionHandler.class
  );

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    LOG.error("Uncaught exception", e);
  }
}
