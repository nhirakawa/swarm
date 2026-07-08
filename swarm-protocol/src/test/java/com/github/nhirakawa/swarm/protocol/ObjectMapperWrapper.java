package com.github.nhirakawa.swarm.protocol;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;

public final class ObjectMapperWrapper {

	private static final ObjectMapper INSTANCE = buildObjectMapper();

	public static ObjectMapper instance() {
		return INSTANCE;
	}

	private static ObjectMapper buildObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.registerModule(new Jdk8Module());
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.registerModule(new GuavaModule());
		objectMapper.registerModule(getInMemoryAddressModule());

		return objectMapper;
	}

	private static Module getInMemoryAddressModule() {
		SimpleModule module = new SimpleModule();
		module.addAbstractTypeMapping(
			SwarmAddress.class,
			InMemorySwarmAddress.class
		);
		return module;
	}

	private ObjectMapperWrapper() {
		throw new UnsupportedOperationException();
	}
}
