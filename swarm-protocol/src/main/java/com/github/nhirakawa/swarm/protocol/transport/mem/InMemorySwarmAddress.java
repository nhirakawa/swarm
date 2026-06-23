package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import org.immutables.value.Value;
import org.jspecify.annotations.NonNull;

@Value.Builder
public record InMemorySwarmAddress(String address) implements SwarmAddress {
	@JsonIgnore
	@Override
	public boolean isMulticastAddress() {
		return "MULTICAST".equals(address);
	}

	@Override
	public @NonNull String asString() {
		return address;
	}
}
