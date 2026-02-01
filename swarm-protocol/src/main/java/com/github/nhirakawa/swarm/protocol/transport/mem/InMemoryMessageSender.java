package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingAck;
import com.github.nhirakawa.swarm.protocol.model.internal.InboundPingRequest;
import com.github.nhirakawa.swarm.protocol.model.internal.PingAckResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.PingRequestResponse;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineResponse;
import java.util.Optional;
import javax.annotation.concurrent.ThreadSafe;

import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of SwarmMessageSender.
 * Routes messages to other nodes via the transport registry.
 */
@ThreadSafe
public class InMemoryMessageSender implements SwarmMessageSender {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryMessageSender.class
  );

  private final SwarmAddress localAddress;
  private final InMemoryTransportRegistry registry;

  public InMemoryMessageSender(
    SwarmAddress localAddress,
    InMemoryTransportRegistry registry
  ) {
    this.localAddress = localAddress;
    this.registry = registry;
  }

  @Override
  public void send(StateMachineResponse response) {
    StateMachineMessage message = convertResponseToMessage(response);
    SwarmAddress targetAddress = getTargetAddress(response);

    Optional<InMemoryTransport> maybeTargetTransport = registry.lookup(
      targetAddress
    );

    if (maybeTargetTransport.isEmpty()) {
      LOG.warn(
        "No transport registered for target address: {}. Message will be dropped: {}",
        targetAddress,
        response
      );
      return;
    }

    InMemoryTransport targetTransport = maybeTargetTransport.get();
    InMemoryMessageReceiver targetReceiver =
      (InMemoryMessageReceiver) targetTransport.receiver();

    boolean enqueued = targetReceiver.enqueue(message);
    if (enqueued) {
      LOG.debug(
        "Sent {} from {} to {}",
        message.getClass().getSimpleName(),
        formatAddress(localAddress),
        formatAddress(targetAddress)
      );
    }
  }

  private String formatAddress(SwarmAddress address) {
    return address.address() + ":" + address.port();
  }

  private StateMachineMessage convertResponseToMessage(
    StateMachineResponse response
  ) {
    return switch (response) {
      case PingRequestResponse pingRequest -> new InboundPingRequest(
        localAddress,
        pingRequest.onBehalfOf(),
        pingRequest.protocolPeriodId()
      );
      case PingAckResponse pingAck -> new InboundPingAck(
        localAddress,
        pingAck.proxyFor(),
        pingAck.protocolPeriodId()
      );
    };
  }

  private SwarmAddress getTargetAddress(StateMachineResponse response) {
    return switch (response) {
      case PingRequestResponse pingRequest -> pingRequest.target();
      case PingAckResponse pingAck -> pingAck.target();
    };
  }
}
