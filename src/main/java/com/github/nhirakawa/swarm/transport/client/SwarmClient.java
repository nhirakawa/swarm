package com.github.nhirakawa.swarm.transport.client;

import java.io.Closeable;
import java.net.InetSocketAddress;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.nhirakawa.swarm.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.config.SwarmNode;
import com.github.nhirakawa.swarm.model.BaseSwarmMessage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class SwarmClient implements Closeable {

  private final SwarmClientChannelInitializer swarmClientChannelInitializer;
  private final SwarmNode localSwarmNode;
  private final EventLoopGroup eventLoopGroup;

  @Inject
  SwarmClient(SwarmClientChannelInitializer swarmClientChannelInitializer,
              SwarmNode localSwarmNode) {
    this.swarmClientChannelInitializer = swarmClientChannelInitializer;
    this.localSwarmNode = localSwarmNode;

    this.eventLoopGroup = new NioEventLoopGroup(
        0,
        new ThreadFactoryBuilder()
            .setNameFormat(String.format("%s-%s", localSwarmNode.getHost(), localSwarmNode.getPort()) + "-%s")
            .build()
    );
  }

  public <M extends BaseSwarmMessage> void send(InetSocketAddress address, M message) throws InterruptedException, JsonProcessingException {
    Bootstrap bootstrap = new Bootstrap();
    Channel channel = bootstrap.group(eventLoopGroup)
        .channel(NioDatagramChannel.class)
        .handler(swarmClientChannelInitializer)
        .bind(0)
        .sync()
        .channel();

    DatagramPacket packet = new DatagramPacket(
        Unpooled.copiedBuffer(ObjectMapperWrapper.instance().writeValueAsBytes(message)),
        address
    );

    channel.writeAndFlush(packet).sync();

    channel.closeFuture().await(1000);
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
  }
}
