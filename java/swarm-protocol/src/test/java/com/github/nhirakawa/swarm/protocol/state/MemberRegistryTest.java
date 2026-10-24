package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.fake.FakeTicker;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberRegistryTest {

	private static final SwarmAddress A = new InMemorySwarmAddress("host-a");
	private static final SwarmAddress B = new InMemorySwarmAddress("host-b");
	private static final Duration RETENTION = Duration.ofSeconds(60);

	@Test
	void itDoesNotEvictConfirmedNodeBeforeRetention() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A), ticker);
		registry.put(A, MemberStatus.confirmed(A, 0L));

		ticker.write(RETENTION.toNanos() - 1);
		registry.evictConfirmedNodes(RETENTION);

		assertThat(registry.get(A)).isPresent();
	}

	@Test
	void itEvictsConfirmedNodeAfterRetention() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A), ticker);
		registry.put(A, MemberStatus.confirmed(A, 0L));

		ticker.write(RETENTION.toNanos());
		registry.evictConfirmedNodes(RETENTION);

		assertThat(registry.get(A)).isEmpty();
		assertThat(registry.size()).isZero();
	}

	@Test
	void itMeasuresRetentionFromConfirmation() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A), ticker);

		ticker.write(RETENTION.toNanos());
		registry.put(A, MemberStatus.confirmed(A, 0L));
		registry.evictConfirmedNodes(RETENTION);

		assertThat(registry.get(A)).isPresent();
	}

	@Test
	void itDoesNotEvictAliveNodes() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A, B), ticker);
		registry.put(B, MemberStatus.confirmed(B, 0L));

		ticker.write(RETENTION.toNanos());
		registry.evictConfirmedNodes(RETENTION);

		assertThat(registry.get(A))
			.isPresent()
			.get()
			.isInstanceOf(MemberStatus.Alive.class);
		assertThat(registry.get(B)).isEmpty();
	}

	@Test
	void itDoesNotResurrectEvictedNodeFromConfirmedGossip() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A), ticker);
		registry.put(A, MemberStatus.confirmed(A, 0L));
		ticker.write(RETENTION.toNanos());
		registry.evictConfirmedNodes(RETENTION);

		registry.put(A, MemberStatus.confirmed(A, 0L));
		registry.put(A, MemberStatus.suspected(A, 0L));
		assertThat(registry.get(A)).isEmpty();

		registry.put(A, MemberStatus.alive(A, 0L));
		assertThat(registry.get(A)).contains(MemberStatus.alive(A, 0L));
	}

	@Test
	void itHasNoPingTargetWhenEveryMemberIsConfirmed() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A, B), ticker);
		registry.put(A, MemberStatus.confirmed(A, 0L));
		registry.put(B, MemberStatus.confirmed(B, 0L));

		assertThat(registry.getPingTarget()).isEmpty();
	}

	@Test
	void itDoesNotEvictRefutedNode() {
		FakeTicker ticker = new FakeTicker();
		MemberRegistry registry = new MemberRegistry(Set.of(A), ticker);
		registry.put(A, MemberStatus.confirmed(A, 0L));
		registry.put(A, MemberStatus.alive(A, 1L));

		ticker.write(RETENTION.toNanos());
		registry.evictConfirmedNodes(RETENTION);

		assertThat(registry.get(A)).contains(MemberStatus.alive(A, 1L));
	}
}
