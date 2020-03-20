package com.github.nhirakawa.swarm.transport.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
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
import java.util.Set;
import javax.inject.Inject;

public class SwarmClient implements Closeable {
  private final SwarmClientChannelInitializer swarmClientChannelInitializer;
  private final SwarmNode localSwarmNode;
  private final Set<SwarmNode> clusterNodes;
  private final EventLoopGroup eventLoopGroup;

  @Inject
  SwarmClient(
    SwarmClientChannelInitializer swarmClientChannelInitializer,
    SwarmNode localSwarmNode,
    Set<SwarmNode> clusterNodes
  ) {
    this.swarmClientChannelInitializer = swarmClientChannelInitializer;
    this.localSwarmNode = localSwarmNode;
    this.clusterNodes = clusterNodes;

    this.eventLoopGroup =
      new NioEventLoopGroup(
        0,
        new ThreadFactoryBuilder()
          .setNameFormat(
            String.format(
              "%s-%s",
              localSwarmNode.getHost(),
              localSwarmNode.getPort()
            ) +
              "-%s"
          )
          .build()
      );
  }

  public CompletableFuture<Void> sendPing(SwarmNode swarmNode)
    throws JsonProcessingException, InterruptedException {
    PingMessage pingMessage = PingMessage
      .builder()
      .setSender(localSwarmNode)
      .build();
    return send(swarmNode, pingMessage);
  }

  public CompletableFuture<Void> sendPingAck(SwarmNode swarmNode)
    throws JsonProcessingException, InterruptedException {
    PingAckMessage pingAckMessage = PingAckMessage
      .builder()
      .setSender(localSwarmNode)
      .build();
    return send(swarmNode, pingAckMessage);
  }

  private <M extends BaseSwarmMessage> CompletableFuture<Void> send(
    SwarmNode swarmNode,
    M message
  )
    throws InterruptedException, JsonProcessingException {
    Bootstrap bootstrap = new Bootstrap();
    Channel channel = bootstrap
      .group(eventLoopGroup)
      .channel(NioDatagramChannel.class)
      .handler(swarmClientChannelInitializer)
      .bind(0)
      .sync()
      .channel();

    InetSocketAddress inetSocketAddress = new InetSocketAddress(
      swarmNode.getHost(),
      swarmNode.getPort()
    );

    DatagramPacket packet = new DatagramPacket(
      Unpooled.copiedBuffer(
        ObjectMapperWrapper.instance().writeValueAsBytes(message)
      ),
      inetSocketAddress
    );

    ChannelFuture channelFuture = channel.writeAndFlush(packet);
    return NettyFutureAdapter.of(channelFuture);
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
  }
}
