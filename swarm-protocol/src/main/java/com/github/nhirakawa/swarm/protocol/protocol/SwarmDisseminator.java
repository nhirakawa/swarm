package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import javax.inject.Inject;

// todo(nhirakawa) document this
public class SwarmDisseminator extends AbstractIdleService {
  private final SwarmConfig swarmConfig;
  private final SwarmMessageSender swarmMessageSender;
  private final EventBus eventBus;

  @Inject
  public SwarmDisseminator(
    SwarmConfig swarmConfig,
    SwarmMessageSender swarmMessageSender,
    EventBus eventBus
  ) {
    this.swarmConfig = swarmConfig;
    this.swarmMessageSender = swarmMessageSender;
    this.eventBus = eventBus;
  }

  @Subscribe
  public void handle(BaseSwarmMessage swarmMessage) {
    if (swarmMessage.getFrom().equals(swarmConfig.getLocalNode())) {
      swarmMessageSender.send(swarmMessage);
    }
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
