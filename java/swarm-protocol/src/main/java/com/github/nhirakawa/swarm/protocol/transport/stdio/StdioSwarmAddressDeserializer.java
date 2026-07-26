package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public class StdioSwarmAddressDeserializer
	extends StdDeserializer<StdioAddress>
{

	public StdioSwarmAddressDeserializer() {
		super(StdioAddress.class);
	}

	@Override
	public StdioAddress deserialize(JsonParser p, DeserializationContext ctxt)
		throws IOException {
		if (p.currentToken() != JsonToken.VALUE_STRING) {
			throw InvalidFormatException.from(
				p,
				"Expected a string for StdioAddress but got " + p.currentToken(),
				p.currentToken(),
				StdioAddress.class
			);
		}
		return new StdioAddress(p.getText());
	}
}
