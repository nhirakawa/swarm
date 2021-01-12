package com.github.nhirakawa.swarm.transport.server;

import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.util.ObjectMapperWrapper;
import com.google.common.eventbus.EventBus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import java.io.IOException;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwarmServerHandler
  extends SimpleChannelInboundHandler<DatagramPacket> {
  private static final Logger LOG = LoggerFactory.getLogger(
    SwarmServerHandler.class
  );

  private final EventBus eventBus;

  @Inject
  SwarmServerHandler(EventBus eventBus) {
    super(false);
    this.eventBus = eventBus;
  }

  @Override
  protected void channelRead0(
    ChannelHandlerContext ctx,
    DatagramPacket message
  )
    throws IOException, InterruptedException {
    byte[] bytes = new byte[message.content().readableBytes()];
    message.content().getBytes(message.content().readerIndex(), bytes);

    BaseSwarmMessage baseSwarmMessage = ObjectMapperWrapper
      .instance()
      .readValue(bytes, BaseSwarmMessage.class);

    eventBus.post(baseSwarmMessage);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOG.error("Exception caught", cause);
  }
}
