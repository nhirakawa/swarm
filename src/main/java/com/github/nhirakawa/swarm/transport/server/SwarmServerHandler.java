package com.github.nhirakawa.swarm.transport.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

public class SwarmServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmServerHandler.class);

  @Inject
  SwarmServerHandler() {
    super(false);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
    ctx.writeAndFlush(
        new DatagramPacket(
            msg.content(),
            msg.sender(),
            msg.recipient()
        )
    );
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOG.error("Exception caught", cause);
  }
}
