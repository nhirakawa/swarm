package com.github.nhirakawa.swarm.transport.client;

import java.io.Closeable;
import java.net.InetSocketAddress;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.model.BaseSwarmMessage;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class SwarmClient implements Closeable {

  private final EventLoopGroup eventLoopGroup;
  private final SwarmClientChannelInitializer swarmClientChannelInitializer;
  private final ObjectMapper objectMapper;

  @Inject
  SwarmClient(SwarmClientChannelInitializer swarmClientChannelInitializer,
              ObjectMapper objectMapper) {
    this.swarmClientChannelInitializer = swarmClientChannelInitializer;
    this.objectMapper = objectMapper;

    this.eventLoopGroup = new NioEventLoopGroup();
  }

  public <M extends BaseSwarmMessage> void send(InetSocketAddress address,
                                                M message) throws InterruptedException, JsonProcessingException {
    Bootstrap bootstrap = new Bootstrap();
    Channel channel = bootstrap.group(eventLoopGroup)
        .channel(NioDatagramChannel.class)
        .handler(swarmClientChannelInitializer)
        .bind("localhost", 8081)
        .channel();

    DatagramPacket packet = new DatagramPacket(
        Unpooled.copiedBuffer(objectMapper.writeValueAsBytes(message)),
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
