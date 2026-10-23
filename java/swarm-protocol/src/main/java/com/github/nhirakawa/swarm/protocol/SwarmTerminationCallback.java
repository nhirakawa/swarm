package com.github.nhirakawa.swarm.protocol;

@FunctionalInterface
public interface SwarmTerminationCallback {
	/**
	 * Called when this node has refuted its own confirmation more often than the
	 * configured limit allows, so it should stop instead of churning its peers' views.
	 */
	void onRepeatedlyConfirmed();
}
