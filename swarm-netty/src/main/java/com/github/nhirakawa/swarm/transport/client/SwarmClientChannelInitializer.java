package com.github.nhirakawa.swarm.transport.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.logging.LogLevel;
import javax.inject.Inject;
import javax.inject.Provider;

public class SwarmClientChannelInitializer
  extends ChannelInitializer<DatagramChannel> {
  private final Provider<SwarmClientHandler> swarmClientHandler;

  @Inject
  SwarmClientChannelInitializer(
    Provider<SwarmClientHandler> swarmClientHandler
  ) {
    this.swarmClientHandler = swarmClientHandler;
  }

  @Override
  protected void initChannel(DatagramChannel channel) {
    channel
      .pipeline()
      .addLast(
        "LoggingHandler",
        new LoggingHandler("SwarmClient", LogLevel.TRACE)
      )
      .addLast("SwarmClientHandler", swarmClientHandler.get());
  }
}
