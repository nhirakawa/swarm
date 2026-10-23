package com.github.nhirakawa.swarm.protocol.transport.stdio;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import org.jspecify.annotations.NonNull;

public record StdioAddress(String address) implements SwarmAddress {
	/** The router's own address, for control lines. */
	public static final StdioAddress ROUTER = new StdioAddress("__router__");

	/** The router copies packets sent here to every Ready node except the sender. */
	public static final StdioAddress BROADCAST = new StdioAddress("__all__");

	@Override
	public boolean isMulticastAddress() {
		return BROADCAST.address.equals(address);
	}

	@Override
	public @NonNull String asString() {
		return address;
	}
}
