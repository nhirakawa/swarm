package com.github.nhirakawa.swarm.runner.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;

public final class Json {

	private static final ObjectMapper OBJECT_MAPPER = buildForLocal();

	private Json() {}

	public static ObjectReader reader() {
		return OBJECT_MAPPER.reader();
	}

	public static ObjectWriter writer() {
		return OBJECT_MAPPER.writer();
	}

	public static ObjectMapper buildForLocal() {
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
}
