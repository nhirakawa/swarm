package com.github.nhirakawa.swarm.nio;

import com.github.nhirakawa.swarm.protocol.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SwarmNioServer
  extends AbstractExecutionThreadService
  implements SwarmMessageSender {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmNioServer.class
  );

  private final EventBus eventBus;
  private final SwarmConfig swarmConfig;
  private final BaseSwarmMessageSerde baseSwarmMessageSerde;

  private DatagramChannel datagramChannel = null;

  @Inject
  SwarmNioServer(
    EventBus eventBus,
    SwarmConfig swarmConfig,
    BaseSwarmMessageSerde baseSwarmMessageSerde
  ) {
    this.eventBus = eventBus;
    this.swarmConfig = swarmConfig;
    this.baseSwarmMessageSerde = baseSwarmMessageSerde;
  }

  @Override
  protected void startUp() throws Exception {
    LOG.debug("Starting nio UDP server - {}", state());
    try {
      eventBus.register(this);

      SwarmNode localNode = swarmConfig.getLocalNode();
      InetSocketAddress inetSocketAddress = new InetSocketAddress(
        localNode.getHost(),
        localNode.getPort()
      );

      datagramChannel = DatagramChannel.open();
      datagramChannel.socket().bind(inetSocketAddress);
    } catch (Exception e) {
      LOG.error("Error starting up", e);
      throw e;
    }
  }

  @Override
  protected void run() throws Exception {
    ByteBuffer incoming = ByteBuffer.allocate(512);

    while (true) {
      InetSocketAddress sender = (InetSocketAddress) datagramChannel.receive(
        incoming
      );

      LOG.trace("Received {} from {}", incoming, sender);

      Optional<BaseSwarmMessage> swarmMessage = baseSwarmMessageSerde.deserialize(
        sender,
        incoming
      );

      eventBus.post(swarmMessage);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    eventBus.unregister(this);
    super.shutDown();
  }

  @Override
  public void send(BaseSwarmMessage swarmMessage) {
    State state = state();
    if (state != State.RUNNING) {
      LOG.debug(
        "Dropping {} because server isn't running ({})",
        swarmMessage,
        state
      );
      return;
    }

    if (swarmMessage.getTo().equals(swarmConfig.getLocalNode())) {
      LOG.debug(
        "Ignoring {} because it is addressed to this node",
        swarmMessage
      );
      return;
    }

    ByteBuffer buffer = baseSwarmMessageSerde.serialize(swarmMessage);
    InetSocketAddress target = InetSocketAddress.createUnresolved(
      swarmMessage.getTo().getHost(),
      swarmMessage.getFrom().getPort()
    );

    try {
      int bytesSent = datagramChannel.send(buffer, target);
      if (bytesSent == 0) {
        LOG.warn("0 bytes written to datagram channel");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected Executor executor() {
    return Executors.newFixedThreadPool(
      4,
      SwarmThreadFactoryFactory.forNode(
        "swarm-nio-udp",
        swarmConfig.getLocalNode()
      )
    );
  }
}
