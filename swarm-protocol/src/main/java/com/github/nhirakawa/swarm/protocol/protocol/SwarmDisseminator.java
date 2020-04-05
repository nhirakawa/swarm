package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.EventBusRegister;
import com.google.common.eventbus.EventBus;

public class SwarmDisseminator implements EventBusRegister {
  private final SwarmMessageSender swarmMessageSender;
  private final EventBus eventBus;

  public SwarmDisseminator(
    SwarmMessageSender swarmMessageSender,
    EventBus eventBus
  ) {
    this.swarmMessageSender = swarmMessageSender;
    this.eventBus = eventBus;
  }

  @Override
  public void register() {
    eventBus.register(this);
  }
}
