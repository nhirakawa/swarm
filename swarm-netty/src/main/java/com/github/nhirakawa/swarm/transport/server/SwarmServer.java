package com.github.nhirakawa.swarm.transport.server;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmProtocol;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmTimer;
import com.google.common.base.Throwables;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class SwarmServer {

  private final EventLoopGroup eventLoopGroup;
  private final SwarmServerChannelInitializer swarmServerChannelInitializer;
  private final SwarmNode localSwarmNode;
  private final SwarmTimer swarmTimer;
  SwarmProtocol swarmProtocol;

  @Inject
  SwarmServer(SwarmServerChannelInitializer swarmServerChannelInitializer,
              SwarmNode localSwarmNode,
              SwarmTimer swarmTimer,
              SwarmProtocol swarmProtocol) {
    this.swarmServerChannelInitializer = swarmServerChannelInitializer;
    this.localSwarmNode = localSwarmNode;
    this.swarmTimer = swarmTimer;
    this.swarmProtocol = swarmProtocol;

    this.eventLoopGroup = new NioEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        SwarmThreadFactoryFactory.forNode("swarm-event-loop-group", localSwarmNode)
    );
  }

  public void start() {
    try {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(eventLoopGroup)
          .channel(NioDatagramChannel.class)
          .handler(swarmServerChannelInitializer)
          .bind(localSwarmNode.getSocketAddress())
          .sync();

      Runtime.getRuntime().addShutdownHook(new Thread(new ServerShutdownHook(eventLoopGroup)));

      swarmProtocol.start();
      swarmTimer.start();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  private static final class ServerShutdownHook implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServerShutdownHook.class);

    private final EventLoopGroup eventLoopGroup;

    private ServerShutdownHook(EventLoopGroup eventLoopGroup) {
      this.eventLoopGroup = eventLoopGroup;
    }

    @Override
    public void run() {
      LOG.info("Shutting down server event loop group");
      eventLoopGroup.shutdownGracefully();
    }
  }
}