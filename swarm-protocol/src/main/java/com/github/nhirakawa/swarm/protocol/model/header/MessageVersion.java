package com.github.nhirakawa.swarm.protocol.model.header;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageVersion {
  V0(0);

  private final int value;

  MessageVersion(int value) {
    this.value = value;
  }

  @JsonCreator
  public static MessageVersion parse(int value) {
    if (value == 0) {
      return MessageVersion.V0;
    } else {
      throw new IllegalArgumentException(
        "%d is not a valid MessageVersion".formatted(value)
      );
    }
  }

  @JsonValue
  public int value() {
    return value;
  }
}
