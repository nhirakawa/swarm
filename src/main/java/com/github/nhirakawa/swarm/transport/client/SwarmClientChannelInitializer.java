package com.github.nhirakawa.swarm.transport.client;

import javax.inject.Inject;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class SwarmClientChannelInitializer extends ChannelInitializer<DatagramChannel> {

  private final SwarmClientHandler swarmClientHandler;

  @Inject
  SwarmClientChannelInitializer(SwarmClientHandler swarmClientHandler) {
    this.swarmClientHandler = swarmClientHandler;
  }

  @Override
  protected void initChannel(DatagramChannel channel) {
    channel.pipeline()
        .addLast("LoggingHandler", new LoggingHandler("SwarmClient", LogLevel.TRACE))
        .addLast("SwarmClientHandler", swarmClientHandler);
  }
}
