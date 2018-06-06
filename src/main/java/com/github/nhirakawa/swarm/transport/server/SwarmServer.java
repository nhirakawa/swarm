package com.github.nhirakawa.swarm.transport.server;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.config.SwarmNode;
import com.github.nhirakawa.swarm.model.SwarmMessageType;
import com.github.nhirakawa.swarm.model.UuidSwarmMessage;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class SwarmServer {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmServer.class);

  private final EventLoopGroup eventLoopGroup;
  private final SwarmServerChannelInitializer swarmServerChannelInitializer;
  private final SwarmNode localSwarmNode;
  private final Set<SwarmNode> clusterNodes;
  private final SwarmClient swarmClient;

  @Inject
  SwarmServer(SwarmServerChannelInitializer swarmServerChannelInitializer,
              SwarmNode localSwarmNode,
              Set<SwarmNode> clusterNodes,
              SwarmClient swarmClient) {
    this.swarmServerChannelInitializer = swarmServerChannelInitializer;
    this.localSwarmNode = localSwarmNode;
    this.clusterNodes = clusterNodes;
    this.swarmClient = swarmClient;

    this.eventLoopGroup = new NioEventLoopGroup(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder()
            .setNameFormat(String.format("%s-%s", localSwarmNode.getHost(), localSwarmNode.getPort()) + "-%s")
            .build()
    );
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

      Runtime.getRuntime().addShutdownHook(new Thread(new ServerShutdownHook(channel)));

      UuidSwarmMessage message = UuidSwarmMessage.builder()
          .setUuid(UUID.randomUUID())
          .setType(SwarmMessageType.UUID)
          .build();

      for (SwarmNode swarmNode : clusterNodes) {
        InetSocketAddress address = new InetSocketAddress(swarmNode.getHost(), swarmNode.getPort());
        swarmClient.send(address, message);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    } catch (JsonProcessingException e) {
      Throwables.throwIfUnchecked(e);
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
