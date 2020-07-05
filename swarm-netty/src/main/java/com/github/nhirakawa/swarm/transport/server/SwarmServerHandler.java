package com.github.nhirakawa.swarm.transport.server;

import com.github.nhirakawa.swarm.ObjectMapperWrapper;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.model.PingAckMessage;
import com.github.nhirakawa.swarm.protocol.model.PingMessage;
import com.github.nhirakawa.swarm.protocol.model.PingProxyRequest;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageApplier;
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

  private final SwarmMessageApplier swarmMessageApplier;

  @Inject
  SwarmServerHandler(SwarmMessageApplier swarmMessageApplier) {
    super(false);
    this.swarmMessageApplier = swarmMessageApplier;
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

    if (baseSwarmMessage instanceof PingMessage) {
      swarmMessageApplier.apply((PingMessage) baseSwarmMessage);
    } else if (baseSwarmMessage instanceof PingAckMessage) {
      swarmMessageApplier.apply((PingAckMessage) baseSwarmMessage);
    } else if (baseSwarmMessage instanceof PingProxyRequest) {
      swarmMessageApplier.apply((PingProxyRequest) baseSwarmMessage);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    LOG.error("Exception caught", cause);
  }
}
