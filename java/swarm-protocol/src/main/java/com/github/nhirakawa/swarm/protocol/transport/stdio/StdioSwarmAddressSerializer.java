package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class StdioSwarmAddressSerializer extends StdSerializer<StdioAddress> {

	public StdioSwarmAddressSerializer() {
		super(StdioAddress.class);
	}

	@Override
	public void serialize(
		StdioAddress value,
		JsonGenerator gen,
		SerializerProvider provider
	) throws IOException {
		gen.writeString(value.address());
	}
}
