package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.AbstractSwarmNode;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingProxyRequest;
import com.github.nhirakawa.swarm.protocol.model.PingResponse;
import com.github.nhirakawa.swarm.protocol.model.PingResponses;
import com.github.nhirakawa.swarm.protocol.model.ProxyTarget;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargetsModel;
import com.github.nhirakawa.swarm.protocol.model.SwarmEnvelope;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.TimeoutResponses;
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

      TimeoutResponses
        .caseOf(timeoutResponse)
        .empty(() -> null)
        .ping(this::sendPingRequest)
        .proxy(this::sendProxyRequest);
    }
  }

  private Void sendPingRequest(AbstractSwarmNode targetModel) {
    SwarmNode target = SwarmNode.builder().from(targetModel).build();

    PingMessage pingMessage = PingMessage
      .builder()
      .setSender(swarmNode)
      .build();

    SwarmEnvelope swarmEnvelope = SwarmEnvelope
      .builder()
      .setBaseSwarmMessage(pingMessage)
      .setToSwarmNode(target)
      .build();

    eventBus.post(swarmEnvelope);

    return null;
  }

  private Void sendProxyRequest(ProxyTargetsModel proxyTargets) {
    for (ProxyTarget proxyTarget : proxyTargets.getProxyTargets()) {
      PingMessage pingMessage = PingMessage
        .builder()
        .setSender(swarmNode)
        .setProxyFor(proxyTarget.getTargetNode())
        .build();

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(pingMessage)
        .setToSwarmNode(proxyTarget.getProxyNode())
        .build();

      eventBus.post(swarmEnvelope);
    }

    return null;
  }

  public void apply(PingMessage pingMessage) {
    if (swarmFailureInjector.shouldInjectFailure()) {
      LOG.debug("Dropping {} because failure is being injected", pingMessage);
      return;
    }

    synchronized (lock) {
      PingResponse pingResponse = swarmProtocol.handle(pingMessage);

      BaseSwarmMessage baseSwarmMessage = PingResponses
        .caseOf(pingResponse)
        .ack(this::ack)
        .proxy(this::toPingMessage);

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(baseSwarmMessage)
        .setToSwarmNode(pingMessage.getSender())
        .build();

      eventBus.post(swarmEnvelope);
    }
  }

  private BaseSwarmMessage ack() {
    return PingAckMessage.builder().setSender(swarmNode).build();
  }

  private BaseSwarmMessage toPingMessage(AbstractSwarmNode abstractSwarmNode) {
    SwarmNode proxyFor = SwarmNode.builder().from(abstractSwarmNode).build();

    return PingMessage
      .builder()
      .setSender(swarmNode)
      .setProxyFor(proxyFor)
      .build();
  }

  public void apply(PingAckMessage pingAckMessage) {
    synchronized (lock) {
      eventBus.post(swarmProtocol.handle(pingAckMessage));
    }
  }

  @Override
  public void initialize() {
    eventBus.register(this);
  }
}
