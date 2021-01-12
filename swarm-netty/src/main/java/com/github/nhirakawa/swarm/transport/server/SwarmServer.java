package com.github.nhirakawa.swarm.transport.server;

import com.github.nhirakawa.swarm.protocol.concurrent.SwarmThreadFactoryFactory;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractIdleService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmServer extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);

  private final EventLoopGroup eventLoopGroup;
  private final SwarmServerChannelInitializer swarmServerChannelInitializer;
  private final SwarmConfig swarmConfig;

  @Inject
  SwarmServer(
    SwarmServerChannelInitializer swarmServerChannelInitializer,
    SwarmConfig swarmConfig
  ) {
    this.swarmServerChannelInitializer = swarmServerChannelInitializer;
    this.swarmConfig = swarmConfig;

    this.eventLoopGroup =
      new NioEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        SwarmThreadFactoryFactory.forNode(
          "swarm-event-loop-group",
          swarmConfig.getLocalNode()
        )
      );
  }

  private static InetSocketAddress toSocketAddress(SwarmNode swarmNode) {
    return new InetSocketAddress(swarmNode.getHost(), swarmNode.getPort());
  }

  private static String threadName(SwarmNode swarmNode) {
    return (
      "swarm-server-shutdown-" + swarmNode.getHost() + "-" + swarmNode.getPort()
    );
  }

  @Override
  protected void startUp() throws Exception {
    try {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap
        .group(eventLoopGroup)
        .channel(NioDatagramChannel.class)
        .handler(swarmServerChannelInitializer)
        .bind(toSocketAddress(swarmConfig.getLocalNode()))
        .sync();

      Runtime
        .getRuntime()
        .addShutdownHook(
          new Thread(
            new ServerShutdownHook(eventLoopGroup),
            threadName(swarmConfig.getLocalNode())
          )
        );
    } catch (InterruptedException e) {
      LOG.error("Could not start UDP server", e);
      Thread.currentThread().interrupt();
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Shutting down server event loop group");
    eventLoopGroup.shutdownGracefully();
  }

  private static final class ServerShutdownHook implements Runnable {
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
