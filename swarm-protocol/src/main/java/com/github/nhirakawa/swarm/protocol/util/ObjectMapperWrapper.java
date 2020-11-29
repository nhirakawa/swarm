package com.github.nhirakawa.swarm.protocol.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class ObjectMapperWrapper {
  private static final ObjectMapper INSTANCE = buildObjectMapper();

  public static ObjectMapper instance() {
    return INSTANCE;
  }

  private static ObjectMapper buildObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();

    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModule(new GuavaModule());

    return objectMapper;
  }

  private ObjectMapperWrapper() {
    throw new UnsupportedOperationException();
  }
}
