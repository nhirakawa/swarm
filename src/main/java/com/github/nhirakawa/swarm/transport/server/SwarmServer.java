package com.github.nhirakawa.swarm.transport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.config.ImmutableSwarmNode;
import com.google.inject.Inject;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class SwarmServer {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);

  private final EventLoopGroup eventLoopGroup;
  private final SwarmServerChannelInitializer swarmServerChannelInitializer;
  private final ImmutableSwarmNode localSwarmNode;

  @Inject
  SwarmServer(SwarmServerChannelInitializer swarmServerChannelInitializer,
              ImmutableSwarmNode localSwarmNode) {
    this.swarmServerChannelInitializer = swarmServerChannelInitializer;
    this.localSwarmNode = localSwarmNode;

    this.eventLoopGroup = new NioEventLoopGroup();
  }

  public void start() {
    try {
      Bootstrap bootstrap = new Bootstrap();
      Channel channel = bootstrap.group(eventLoopGroup)
          .channel(NioDatagramChannel.class)
          .handler(swarmServerChannelInitializer)
          .bind(localSwarmNode.getSocketAddress())
          .sync()
          .channel();

      LOG.info("Listening on {}", localSwarmNode.getSocketAddress());

      Runtime.getRuntime().addShutdownHook(new Thread(new ServerShutdownHook(channel)));

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private final class ServerShutdownHook implements Runnable {

    private final Channel channel;

    private ServerShutdownHook(Channel channel) {
      this.channel = channel;
    }

    @Override
    public void run() {
      try {
        LOG.info("Netty channel shutdown starting");
        channel.closeFuture().await(1000);
        LOG.info("Netty channel shutdown complete");
      } catch (InterruptedException e) {
        LOG.error("Caught exception when shutting down channel in shutdown hook", e);
      } finally {
        LOG.info("Shutting down server event loop group");
        eventLoopGroup.shutdownGracefully();
      }
    }
  }
}
