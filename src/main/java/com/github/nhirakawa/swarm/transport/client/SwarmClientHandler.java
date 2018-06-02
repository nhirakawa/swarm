package com.github.nhirakawa.swarm.transport.client;

import java.util.concurrent.BlockingQueue;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nhirakawa.swarm.dagger.IncomingQueue;
import com.github.nhirakawa.swarm.model.BaseSwarmMessage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

class SwarmClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

  private static final Logger LOG = LoggerFactory.getLogger(SwarmClientHandler.class);

  private final ObjectMapper objectMapper;
  private final BlockingQueue<BaseSwarmMessage> incomingQueue;

  @Inject
  SwarmClientHandler(ObjectMapper objectMapper,
                     @IncomingQueue BlockingQueue<BaseSwarmMessage> incomingQueue) {
    super(true);
    this.objectMapper = objectMapper;
    this.incomingQueue = incomingQueue;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext context, DatagramPacket message) throws Exception {
    byte[] messageBytes = new byte[message.content().readableBytes()];
    message.content().getBytes(message.content().readerIndex(), messageBytes);

    BaseSwarmMessage incomingMessage = objectMapper.readValue(messageBytes, BaseSwarmMessage.class);
    LOG.info("Received message {}", incomingMessage);
    incomingQueue.put(incomingMessage);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    LOG.error("Exception", cause);
  }
}
