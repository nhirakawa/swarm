package com.github.nhirakawa.swarm.transport.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.io.Closeable;
import java.net.BindException;
import java.net.InetSocketAddress;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmClient implements Closeable, SwarmMessageSender {
  private static final Logger LOG = LoggerFactory.getLogger(SwarmClient.class);

  private final SwarmClientChannelInitializer swarmClientChannelInitializer;
  private final SwarmConfig swarmConfig;
  private final EventLoopGroup eventLoopGroup;

  @Inject
  SwarmClient(
    SwarmClientChannelInitializer swarmClientChannelInitializer,
    SwarmConfig swarmConfig
  ) {
    this.swarmClientChannelInitializer = swarmClientChannelInitializer;
    this.swarmConfig = swarmConfig;

    this.eventLoopGroup =
      new NioEventLoopGroup(
        0,
        new ThreadFactoryBuilder()
          .setNameFormat(
            String.format(
              "%s-%s",
              swarmConfig.getLocalNode().getHost(),
              swarmConfig.getLocalNode().getPort()
            ) +
              "-%s"
          )
          .build()
      );
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
  }

  @Override
  public void send(BaseSwarmMessage swarmEnvelope) {
    if (!swarmEnvelope.getFrom().equals(swarmConfig.getLocalNode())) {
      LOG.warn("Not sending message from different node {}", swarmEnvelope);
      return;
    }

    LOG.debug("Sending {}", swarmEnvelope);

    try {
      Channel channel = buildChannel();
      DatagramPacket datagramPacket = wrapInDatagramPacket(
        swarmEnvelope.getTo(),
        swarmEnvelope
      );
      channel.writeAndFlush(datagramPacket).sync().channel().close().sync();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (Throwable t) {
      LOG.error("Caught exception", t);

      if (t instanceof BindException) {
        LOG.error("Could not bind to port 0", t);
      } else {
        LOG.error("Caught exception", t);
      }

      Throwables.throwIfUnchecked(t);
      throw new RuntimeException(t);
    }
  }

  private Channel buildChannel() {
    try {
      Bootstrap bootstrap = new Bootstrap();
      return bootstrap
        .group(eventLoopGroup)
        .channel(NioDatagramChannel.class)
        .handler(swarmClientChannelInitializer)
        .bind(0)
        .sync()
        .channel();
    } catch (InterruptedException e) {
      LOG.error("Caught exception building channel", e);
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private <M extends BaseSwarmMessage> DatagramPacket wrapInDatagramPacket(
    SwarmNode swarmNode,
    M message
  ) {
    InetSocketAddress inetSocketAddress = new InetSocketAddress(
      swarmNode.getHost(),
      swarmNode.getPort()
    );

    try {
      return new DatagramPacket(
        Unpooled.copiedBuffer(
          ObjectMapperWrapper.instance().writeValueAsBytes(message)
        ),
        inetSocketAddress
      );
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
