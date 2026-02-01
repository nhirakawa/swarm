package com.github.nhirakawa.swarm.protocol.model.serde.header;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Compression {
  NONE(0),
  GZIP(1);

  private final int value;

  Compression(int value) {
    this.value = value;
  }

  @JsonCreator
  public static Compression parse(int value) {
    return switch (value) {
      case 0 -> Compression.NONE;
      case 1 -> Compression.GZIP;
      default -> throw new IllegalArgumentException(
        "%d is not a valid Compression value".formatted(value)
      );
    };
  }

  @JsonValue
  public int value() {
    return value;
  }
}
