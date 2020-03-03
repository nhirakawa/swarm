package com.github.nhirakawa.swarm;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectMapperWrapper {
  private static final ObjectMapper INSTANCE = new ObjectMapper();

  private ObjectMapperWrapper() {}

  public static ObjectMapper instance() {
    return INSTANCE;
  }
}
