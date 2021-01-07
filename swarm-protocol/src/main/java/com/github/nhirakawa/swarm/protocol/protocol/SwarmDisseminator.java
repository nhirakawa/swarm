package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import javax.inject.Inject;

public class SwarmDisseminator extends AbstractIdleService {
  private final SwarmMessageSender swarmMessageSender;
  private final EventBus eventBus;

  @Inject
  public SwarmDisseminator(
    SwarmMessageSender swarmMessageSender,
    EventBus eventBus
  ) {
    this.swarmMessageSender = swarmMessageSender;
    this.eventBus = eventBus;
  }

  @Subscribe
  public void handle(BaseSwarmMessage swarmMessage) {
    swarmMessageSender.send(swarmMessage);
  }

  @Override
  protected void startUp() throws Exception {
    eventBus.register(this);
  }

  @Override
  protected void shutDown() throws Exception {
    eventBus.unregister(this);
  }
}
