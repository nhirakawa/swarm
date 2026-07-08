package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberRegistryTest {

	private static final SwarmAddress A = new InMemorySwarmAddress("host-a");
	private static final SwarmAddress B = new InMemorySwarmAddress("host-b");

	@Test
	void itDoesNotEvictConfirmedNodeBelowGossipThreshold() {
		// With 1 member, threshold = max(ceil(log2(2)), 3) = 3
		MemberRegistry registry = new MemberRegistry(Set.of(A));
		registry.put(A, MemberStatus.confirmed(A, 0L));

		// Gossip twice — below threshold of 3
		registry.getGossipPayload(1);
		registry.getGossipPayload(1);

		registry.evictConfirmedNodes();

		assertThat(registry.get(A)).isPresent();
	}

	@Test
	void itEvictsConfirmedNodeAtGossipThreshold() {
		// With 1 member, threshold = max(ceil(log2(2)), 3) = 3
		MemberRegistry registry = new MemberRegistry(Set.of(A));
		registry.put(A, MemberStatus.confirmed(A, 0L));

		// Gossip 3 times — meets threshold
		registry.getGossipPayload(1);
		registry.getGossipPayload(1);
		registry.getGossipPayload(1);

		registry.evictConfirmedNodes();

		assertThat(registry.get(A)).isEmpty();
		assertThat(registry.size()).isZero();
	}

	@Test
	void itDoesNotEvictAliveNodeWithHighGossipCount() {
		MemberRegistry registry = new MemberRegistry(Set.of(A));

		// Gossip many times
		for (int i = 0; i < 10; i++) {
			registry.getGossipPayload(1);
		}

		registry.evictConfirmedNodes();

		assertThat(registry.get(A))
			.isPresent()
			.get()
			.isInstanceOf(MemberStatus.Alive.class);
	}

	@Test
	void itOnlyEvictsConfirmedNodesNotAliveOnes() {
		MemberRegistry registry = new MemberRegistry(Set.of(A, B));
		registry.put(B, MemberStatus.confirmed(B, 0L));

		// Gossip enough times to meet threshold for B
		// threshold = max(ceil(log2(3)), 3) = max(2, 3) = 3
		registry.getGossipPayload(2);
		registry.getGossipPayload(2);
		registry.getGossipPayload(2);

		registry.evictConfirmedNodes();

		assertThat(registry.get(A))
			.isPresent()
			.get()
			.isInstanceOf(MemberStatus.Alive.class);
		assertThat(registry.get(B)).isEmpty();
	}

	@Test
	void itUsesFloorOfThreeForSmallClusters() {
		// 1-member registry: threshold = max(ceil(log2(2)), 3) = max(1, 3) = 3
		MemberRegistry registry = new MemberRegistry(Set.of(A));
		registry.put(A, MemberStatus.confirmed(A, 0L));

		registry.getGossipPayload(1);
		registry.getGossipPayload(1);
		registry.evictConfirmedNodes();
		assertThat(registry.get(A)).isPresent(); // not yet evicted at count=2

		registry.getGossipPayload(1);
		registry.evictConfirmedNodes();
		assertThat(registry.get(A)).isEmpty(); // evicted at count=3
	}

	@Test
	void itUsesLargerThresholdForLargerClusters() {
		// Build a 15-member registry: threshold = max(ceil(log2(16)), 3) = max(4, 3) = 4
		Set<SwarmAddress> members = new java.util.HashSet<>();
		for (int i = 0; i < 15; i++) {
			members.add(new InMemorySwarmAddress("host-" + i));
		}
		MemberRegistry registry = new MemberRegistry(members);

		SwarmAddress target = new InMemorySwarmAddress("host-0");
		registry.put(target, MemberStatus.confirmed(target, 0L));

		// Gossip all members each round so target accumulates count reliably
		// 3 rounds: target gossipCount=3, below threshold of 4
		registry.getGossipPayload(15);
		registry.getGossipPayload(15);
		registry.getGossipPayload(15);
		registry.evictConfirmedNodes();
		assertThat(registry.get(target)).isPresent();

		// 4th round: target gossipCount=4, meets threshold
		registry.getGossipPayload(15);
		registry.evictConfirmedNodes();
		assertThat(registry.get(target)).isEmpty();
	}
}
