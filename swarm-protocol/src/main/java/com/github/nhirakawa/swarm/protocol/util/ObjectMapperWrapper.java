package com.github.nhirakawa.swarm.protocol.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectMapperWrapper {
  private static final ObjectMapper INSTANCE = buildObjectMapper();

  public static ObjectMapper instance() {
    return INSTANCE;
  }

  private static ObjectMapper buildObjectMapper() {
    return new ObjectMapper();
  }

  private ObjectMapperWrapper() {
    throw new UnsupportedOperationException();
  }
}
