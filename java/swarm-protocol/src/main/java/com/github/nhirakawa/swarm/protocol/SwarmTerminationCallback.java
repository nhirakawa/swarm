package com.github.nhirakawa.swarm.protocol;

@FunctionalInterface
public interface SwarmTerminationCallback {
	void onSelfConfirmedDead();
}
