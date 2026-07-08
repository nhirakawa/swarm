package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public class InMemorySwarmAddressDeserializer
	extends StdDeserializer<InMemorySwarmAddress>
{

	public InMemorySwarmAddressDeserializer() {
		super(InMemorySwarmAddress.class);
	}

	@Override
	public InMemorySwarmAddress deserialize(
		JsonParser p,
		DeserializationContext ctxt
	) throws IOException {
		if (p.currentToken() != JsonToken.VALUE_STRING) {
			throw InvalidFormatException.from(
				p,
				"Expected a string for InMemorySwarmAddress but got " +
					p.currentToken(),
				p.currentToken(),
				InMemorySwarmAddress.class
			);
		}
		return new InMemorySwarmAddress(p.getText());
	}
}
