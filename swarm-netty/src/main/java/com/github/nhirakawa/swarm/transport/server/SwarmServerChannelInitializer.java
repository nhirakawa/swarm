package com.github.nhirakawa.swarm.transport.server;

import javax.inject.Inject;
import javax.inject.Provider;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class SwarmServerChannelInitializer extends ChannelInitializer<DatagramChannel> {

  private final Provider<SwarmServerHandler> swarmServerHandler;

  @Inject
  SwarmServerChannelInitializer(Provider<SwarmServerHandler> swarmServerHandler) {
    this.swarmServerHandler = swarmServerHandler;
  }

  @Override
  protected void initChannel(DatagramChannel channel) {
    channel.pipeline()
        .addLast("LoggingHandler", new LoggingHandler("SwarmServer", LogLevel.TRACE))
        .addLast("SwarmServerHandler", swarmServerHandler.get());
  }

}
