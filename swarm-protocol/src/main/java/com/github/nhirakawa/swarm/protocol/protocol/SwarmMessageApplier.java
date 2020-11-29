package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.config.SwarmNodeModel;
import com.github.nhirakawa.swarm.protocol.Initializable;
import com.github.nhirakawa.swarm.protocol.model.ack.PingAck;
import com.github.nhirakawa.swarm.protocol.model.ack.PingAckError;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.ping.PingProxy;
import com.github.nhirakawa.swarm.protocol.model.ping.PingResponse;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargetModel;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargetsModel;
import com.github.nhirakawa.swarm.protocol.model.SwarmEnvelope;
import com.github.nhirakawa.swarm.protocol.model.SwarmTimeoutMessage;
import com.github.nhirakawa.swarm.protocol.model.timeout.EmptyTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.PingProxyTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.PingTimeoutResponse;
import com.github.nhirakawa.swarm.protocol.model.timeout.TimeoutResponse;
import com.google.common.eventbus.EventBus;
import com.hubspot.algebra.Result;
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
  private final SwarmFailureInjector swarmFailureInjector;

  @Inject
  public SwarmMessageApplier(
    SwarmConfig swarmConfig,
    SwarmProtocol swarmProtocol,
    EventBus eventBus,
    SwarmFailureInjector swarmFailureInjector
  ) {
    this.swarmNode = swarmConfig.getLocalNode();
    this.swarmProtocol = swarmProtocol;
    this.eventBus = eventBus;
    this.swarmFailureInjector = swarmFailureInjector;
  }

  public void apply(SwarmTimeoutMessage swarmTimeoutMessage) {
    synchronized (lock) {
      TimeoutResponse timeoutResponse = swarmProtocol.handle(
        swarmTimeoutMessage
      );

      if (timeoutResponse instanceof EmptyTimeoutResponse) {
        return;
      }

      if (timeoutResponse instanceof PingTimeoutResponse) {
        PingTimeoutResponse pingTimeoutResponse = (PingTimeoutResponse) timeoutResponse;

        sendPingRequest(
          pingTimeoutResponse.getProtocolId(),
          pingTimeoutResponse.getSwarmNode()
        );
        return;
      }

      if (timeoutResponse instanceof PingProxyTimeoutResponse) {
        PingProxyTimeoutResponse pingProxyTimeoutResponse = (PingProxyTimeoutResponse) timeoutResponse;

        sendProxyRequest(
          pingProxyTimeoutResponse.getProtocolId(),
          pingProxyTimeoutResponse.getProxyTargets()
        );
      }
    }
  }

  private Void sendPingRequest(String protocolId, SwarmNodeModel targetModel) {
    SwarmNode target = SwarmNode.builder().from(targetModel).build();

    PingMessage pingMessage = PingMessage
      .builder()
      .setSender(swarmNode)
      .setProtocolPeriodId(protocolId)
      .build();

    SwarmEnvelope swarmEnvelope = SwarmEnvelope
      .builder()
      .setBaseSwarmMessage(pingMessage)
      .setToSwarmNode(target)
      .build();

    eventBus.post(swarmEnvelope);

    return null;
  }

  private Void sendProxyRequest(
    String protocolId,
    ProxyTargetsModel proxyTargets
  ) {
    for (ProxyTargetModel proxyTarget : proxyTargets.getProxyTargets()) {
      PingMessage pingMessage = PingMessage
        .builder()
        .setSender(swarmNode)
        .setProxyFor(
          SwarmNode.builder().from(proxyTarget.getTargetNode()).build()
        )
        .setProtocolPeriodId(protocolId)
        .build();

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(pingMessage)
        .setToSwarmNode(
          SwarmNode.builder().from(proxyTarget.getProxyNode()).build()
        )
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

      final BaseSwarmMessage baseSwarmMessage;
      if (
        pingResponse instanceof com.github.nhirakawa.swarm.protocol.model.ping.PingAck
      ) {
        baseSwarmMessage = ack(pingMessage.getProtocolPeriodId());
      } else if (pingResponse instanceof PingProxy) {
        PingProxy pingProxy = (PingProxy) pingResponse;

        baseSwarmMessage =
          toPingMessage(
            pingMessage.getProtocolPeriodId(),
            pingProxy.getSwarmNode()
          );
      } else {
        throw new IllegalArgumentException();
      }

      SwarmEnvelope swarmEnvelope = SwarmEnvelope
        .builder()
        .setBaseSwarmMessage(baseSwarmMessage)
        .setToSwarmNode(pingMessage.getSender())
        .build();

      eventBus.post(swarmEnvelope);
    }
  }

  private BaseSwarmMessage ack(String protocolPeriodId) {
    return PingAckMessage.builder().setSender(swarmNode).setProtocolPeriodId(protocolPeriodId).build();
  }

  private BaseSwarmMessage toPingMessage(
    String protocolPeriodId,
    SwarmNodeModel swarmNodeModel
  ) {
    SwarmNode proxyFor = SwarmNode.builder().from(swarmNodeModel).build();

    return PingMessage
      .builder()
      .setSender(swarmNode)
      .setProxyFor(proxyFor)
      .setProtocolPeriodId(protocolPeriodId)
      .build();
  }

  public void apply(PingAckMessage pingAckMessage) {
    synchronized (lock) {
      Result<PingAck, PingAckError> result = swarmProtocol.handle(
        pingAckMessage
      );

      if (result.isErr()) {
        return;
      }

      eventBus.post(result.unwrapOrElseThrow());
    }
  }

  @Override
  public void initialize() {
    eventBus.register(this);
  }
}
