package com.github.nhirakawa.swarm.protocol.model;

import com.google.common.primitives.UnsignedBytes;
import java.util.Optional;

public enum SwarmMessageType {
  PING_ACK(0), PING_REQUEST(1);
  private final byte id;

  SwarmMessageType(int id) {
    this.id = UnsignedBytes.checkedCast(id);
  }

  public byte getId() {
    return id;
  }

  public static Optional<SwarmMessageType> fromId(byte id) {
    switch (id) {
      case 0:
        return Optional.of(PING_ACK);
      case 1:
        return Optional.of(PING_REQUEST);
      default:
        return Optional.empty();
    }
  }
}
