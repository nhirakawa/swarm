package com.github.nhirakawa.swarm.transport.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.transport.NettyFutureAdapter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

public class SwarmClient implements Closeable, SwarmMessageSender {
  private final SwarmClientChannelInitializer swarmClientChannelInitializer;
  private final EventLoopGroup eventLoopGroup;

  @Inject
  SwarmClient(
    SwarmClientChannelInitializer swarmClientChannelInitializer,
    SwarmConfig swarmConfig
  ) {
    this.swarmClientChannelInitializer = swarmClientChannelInitializer;

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
  public CompletableFuture<?> send(BaseSwarmMessage swarmEnvelope) {
    Channel channel = buildChannel();
    DatagramPacket datagramPacket = wrapInDatagramPacket(
      swarmEnvelope.getTo(),
      swarmEnvelope
    );

    ChannelFuture channelFuture = channel
      .writeAndFlush(datagramPacket)
      .channel()
      .closeFuture();
    return NettyFutureAdapter.of(channelFuture);
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
