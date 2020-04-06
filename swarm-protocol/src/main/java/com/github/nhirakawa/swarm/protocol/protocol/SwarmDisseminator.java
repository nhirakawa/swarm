package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.SwarmEnvelope;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javax.inject.Inject;

public class SwarmDisseminator implements Initializable {
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

  @Override
  public void initialize() {
    eventBus.register(this);
  }

  @Subscribe
  public void handle(SwarmEnvelope swarmEnvelope) {
    swarmMessageSender.send(swarmEnvelope);
  }
}
