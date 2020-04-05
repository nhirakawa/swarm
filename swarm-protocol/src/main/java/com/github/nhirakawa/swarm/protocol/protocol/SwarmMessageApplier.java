package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.EventBusRegister;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.SwarmEnvelope;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.google.common.eventbus.EventBus;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmMessageApplier implements EventBusRegister {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmMessageApplier.class
  );

  private final SwarmNode swarmNode;
  private final SwarmProtocol swarmProtocol;
  private final EventBus eventBus;

  @Inject
  public SwarmMessageApplier(
    SwarmNode swarmNode,
    SwarmProtocol swarmProtocol,
    EventBus eventBus
  ) {
    this.swarmNode = swarmNode;
    this.swarmProtocol = swarmProtocol;
    this.eventBus = eventBus;
  }

  public void apply(SwarmTimeoutMessage swarmTimeoutMessage) {
    TimeoutResponse timeoutResponse = swarmProtocol.handle(swarmTimeoutMessage);
    if (timeoutResponse.getTargetNode().isPresent()) {
      PingMessage pingMessage = PingMessage
        .builder()
        .setSender(swarmNode)
        .build();

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(pingMessage)
        .setToSwarmNode(timeoutResponse.getTargetNode().get())
        .build();

      LOG.trace("Sending {}", swarmEnvelope);

      eventBus.post(swarmEnvelope);
    } else {
      LOG.trace("No target node in timeout response");
    }
  }

  public void apply(PingMessage pingMessage) {
    PingAckMessage pingAckMessage = swarmProtocol.handle(pingMessage);

    SwarmEnvelope swarmEnvelope = SwarmEnvelope
      .builder()
      .setBaseSwarmMessage(pingAckMessage)
      .setToSwarmNode(pingMessage.getSender())
      .build();

    eventBus.post(swarmEnvelope);
  }

  public void apply(PingAckMessage pingAckMessage) {
    eventBus.post(swarmProtocol.handle(pingAckMessage));
  }

  public void init() {}

  @Override
  public void register() {
    eventBus.register(this);
  }
}
