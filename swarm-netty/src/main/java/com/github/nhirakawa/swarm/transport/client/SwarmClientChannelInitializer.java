package com.github.nhirakawa.swarm.transport.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.compression.BrotliDecoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.logging.LogLevel;
import javax.inject.Inject;

public class SwarmClientChannelInitializer
  extends ChannelInitializer<DatagramChannel> {

  @Inject
  SwarmClientChannelInitializer() {}

  @Override
  protected void initChannel(DatagramChannel channel) {
    channel
      .pipeline()
      .addLast(new BrotliDecoder())
      .addLast(
        "LoggingHandler",
        new LoggingHandler(SwarmClient.class, LogLevel.TRACE)
      );
  }
}
