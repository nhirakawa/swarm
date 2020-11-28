package com.github.nhirakawa.swarm.protocol.model.timeout;

public class EmptyTimeoutResponse implements TimeoutResponse {
  private static final EmptyTimeoutResponse INSTANCE = new EmptyTimeoutResponse();

  private EmptyTimeoutResponse() {}

  public static EmptyTimeoutResponse instance() {
    return INSTANCE;
  }
}
