package com.github.nhirakawa.swarm.protocol.model.ping;

public class PingAck extends PingResponse {
  private static final PingAck INSTANCE = new PingAck();

  private PingAck() {}

  public static PingAck instance() {
    return INSTANCE;
  }
}
