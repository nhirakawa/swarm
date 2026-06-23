package com.github.nhirakawa.swarm.protocol.model.address;

import org.jspecify.annotations.NonNull;

public interface SwarmAddress {
	boolean isMulticastAddress();
	@NonNull
	String asString();
}
