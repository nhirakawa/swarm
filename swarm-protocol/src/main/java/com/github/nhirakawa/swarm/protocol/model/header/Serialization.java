package com.github.nhirakawa.swarm.protocol.model.header;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Serialization {
  JSON(0),
  CBOR(1);

  private final int value;

  Serialization(int value) {
    this.value = value;
  }

  @JsonCreator
  public static Serialization parse(int value) {
    return switch (value) {
      case 0 -> Serialization.JSON;
      case 1 -> Serialization.CBOR;
      default -> throw new IllegalArgumentException(
        "%d is not a valid Serialization value".formatted(value)
      );
    };
  }

  @JsonValue
  public int value() {
    return value;
  }
}
