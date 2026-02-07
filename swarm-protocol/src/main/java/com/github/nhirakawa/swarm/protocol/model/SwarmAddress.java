package com.github.nhirakawa.swarm.protocol.model;

/**
 * @param address The IP address of the swarm member
 * @param port The port of the swarm member
 * @param uid The unique ID of the swarm member
 */
public record SwarmAddress(String address, int port, String uid) {
	public static SwarmAddress createMulticastAddress() {
		return new SwarmAddress("224.0.2.1", 0, "224.0.2.1:0");
	}
}
