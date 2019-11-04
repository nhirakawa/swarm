package com.github.nhirakawa.swarm.transport.client;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.nhirakawa.swarm.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmProtocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

class SwarmClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmClientHandler.class);

  private final SwarmProtocol swarmProtocol;

  @Inject
  SwarmClientHandler(SwarmProtocol swarmProtocol) {
    this.swarmProtocol = swarmProtocol;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext context, DatagramPacket message) throws Exception {
    byte[] messageBytes = new byte[message.content().readableBytes()];
    message.content().getBytes(message.content().readerIndex(), messageBytes);

    BaseSwarmMessage incomingMessage = ObjectMapperWrapper.instance().readValue(messageBytes, BaseSwarmMessage.class);

    if (incomingMessage instanceof PingAckMessage) {
      swarmProtocol.handle((PingAckMessage) incomingMessage);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    LOG.error("Exception", cause);
  }
}
