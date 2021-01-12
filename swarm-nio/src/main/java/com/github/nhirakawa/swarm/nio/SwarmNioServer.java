package com.github.nhirakawa.swarm.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Optional;

import javax.inject.Inject;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

public class SwarmNioServer extends AbstractExecutionThreadService {
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
    eventBus.register(this);

    SwarmNode localNode = swarmConfig.getLocalNode();
    InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(
      localNode.getHost(),
      localNode.getPort()
    );

    datagramChannel = DatagramChannel.open();
    datagramChannel.socket().bind(inetSocketAddress);
  }

  @Override
  protected void run() throws Exception {
    ByteBuffer incoming = ByteBuffer.allocate(512);

    while (true) {
      InetSocketAddress sender = (InetSocketAddress) datagramChannel.receive(
        incoming
      );

      Optional<BaseSwarmMessage> swarmMessage = baseSwarmMessageSerde.deserialize(
        sender,
        incoming
      );

      eventBus.post(swarmMessage);
    }
  }

  @Subscribe
  public void handle(BaseSwarmMessage swarmMessage) throws IOException {
    if (state() != State.RUNNING) {
      return;
    }

    if (swarmMessage.getTo().equals(swarmConfig.getLocalNode())) {
      return;
    }

    ByteBuffer buffer = baseSwarmMessageSerde.serialize(swarmMessage);
    InetSocketAddress target = InetSocketAddress.createUnresolved(
      swarmMessage.getTo().getHost(),
      swarmMessage.getFrom().getPort()
    );

    datagramChannel.send(buffer, target);
  }

  @Override
  protected void shutDown() throws Exception {
    eventBus.unregister(this);
    super.shutDown();
  }
}
