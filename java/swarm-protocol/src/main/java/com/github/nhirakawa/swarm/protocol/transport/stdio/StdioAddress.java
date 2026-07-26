package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import org.jspecify.annotations.NonNull;

public record StdioAddress(String address) implements SwarmAddress {
	@Override
	public boolean isMulticastAddress() {
		return "MULTICAST".equals(address);
	}

	@Override
	public @NonNull String asString() {
		return address;
	}
}
