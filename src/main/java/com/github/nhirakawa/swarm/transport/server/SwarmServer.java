package com.github.nhirakawa.swarm.transport.server;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.config.ImmutableSwarmNode;
import com.github.nhirakawa.swarm.model.ImmutableUuidSwarmMessage;
import com.github.nhirakawa.swarm.model.SwarmMessageType;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
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
  private final SwarmClient swarmClient;

  @Inject
  SwarmServer(SwarmServerChannelInitializer swarmServerChannelInitializer,
              ImmutableSwarmNode localSwarmNode,
              SwarmClient swarmClient) {
    this.swarmServerChannelInitializer = swarmServerChannelInitializer;
    this.localSwarmNode = localSwarmNode;
    this.swarmClient = swarmClient;

    this.eventLoopGroup = new NioEventLoopGroup();
  }

  public void start() throws JsonProcessingException {
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

      ImmutableUuidSwarmMessage message = ImmutableUuidSwarmMessage.builder()
          .uuid(UUID.randomUUID())
          .type(SwarmMessageType.UUID)
          .build();

      InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
      swarmClient.send(address, message);

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
