package com.github.nhirakawa.swarm.protocol.util;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeadEventLogger extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(
    DeadEventLogger.class
  );

  private final EventBus eventBus;

  @Inject
  DeadEventLogger(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  protected void startUp() throws Exception {
    eventBus.register(this);
  }

  @Override
  protected void shutDown() throws Exception {
    eventBus.unregister(this);
  }

  @Subscribe
  public void logDeadEvent(DeadEvent deadEvent) {
    LOG.warn("Found dead event - {}", deadEvent);
  }
}