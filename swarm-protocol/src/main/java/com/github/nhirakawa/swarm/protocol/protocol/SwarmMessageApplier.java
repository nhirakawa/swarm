package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingProxyRequest;
import com.github.nhirakawa.swarm.protocol.model.SwarmEnvelope;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.google.common.eventbus.EventBus;
import com.typesafe.config.Config;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmMessageApplier implements Initializable {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmMessageApplier.class
  );

  private final Object lock = new Object();

  private final SwarmNode swarmNode;
  private final SwarmProtocol swarmProtocol;
  private final EventBus eventBus;
  private final Config config;
  private final SwarmFailureInjector swarmFailureInjector;

  @Inject
  public SwarmMessageApplier(
    SwarmNode swarmNode,
    SwarmProtocol swarmProtocol,
    EventBus eventBus,
    Config config,
    SwarmFailureInjector swarmFailureInjector
  ) {
    this.swarmNode = swarmNode;
    this.swarmProtocol = swarmProtocol;
    this.eventBus = eventBus;
    this.config = config;
    this.swarmFailureInjector = swarmFailureInjector;
  }

  public void apply(SwarmTimeoutMessage swarmTimeoutMessage) {
    synchronized (lock) {
      TimeoutResponse timeoutResponse = swarmProtocol.handle(
        swarmTimeoutMessage
      );

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
  }

  public void apply(PingMessage pingMessage) {
    if (swarmFailureInjector.shouldInjectFailure()) {
      LOG.debug("Dropping {} because failure is being injected", pingMessage);
      return;
    }

    synchronized (lock) {
      PingAckMessage pingAckMessage = swarmProtocol.handle(pingMessage);

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(pingAckMessage)
        .setToSwarmNode(pingMessage.getSender())
        .build();

      eventBus.post(swarmEnvelope);
    }
  }

  public void apply(PingAckMessage pingAckMessage) {
    synchronized (lock) {
      eventBus.post(swarmProtocol.handle(pingAckMessage));
    }
  }

  public void apply(PingProxyRequest pingProxyRequest) {
    if (swarmFailureInjector.shouldInjectFailure()) {
      LOG.debug(
        "Dropping {} because failure is being injected",
        pingProxyRequest
      );
      return;
    }

    synchronized (lock) {
      swarmProtocol.handle(pingProxyRequest);
    }
  }

  @Override
  public void initialize() {
    eventBus.register(this);
  }
}
