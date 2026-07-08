package com.github.nhirakawa.swarm.protocol.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.mem.InMemorySwarmAddress;
import org.junit.jupiter.api.Test;

class MemberStatusTest {

	private static final SwarmAddress ADDRESS = new InMemorySwarmAddress("asdf");

	@Test
	void testAliveMergeWithAlive_higherIncarnation() {
		MemberStatus.Alive alive1 = new MemberStatus.Alive(ADDRESS, 1);
		MemberStatus.Alive alive2 = new MemberStatus.Alive(ADDRESS, 5);

		MemberStatus result = alive1.merge(alive2);

		assertThat(result).isEqualTo(new MemberStatus.Alive(ADDRESS, 5));
	}

	@Test
	void testAliveMergeWithAlive_lowerIncarnation() {
		MemberStatus.Alive alive1 = new MemberStatus.Alive(ADDRESS, 10);
		MemberStatus.Alive alive2 = new MemberStatus.Alive(ADDRESS, 3);

		MemberStatus result = alive1.merge(alive2);

		assertThat(result).isEqualTo(new MemberStatus.Alive(ADDRESS, 10));
	}

	@Test
	void testAliveMergeWithAlive_sameIncarnation() {
		MemberStatus.Alive alive1 = new MemberStatus.Alive(ADDRESS, 5);
		MemberStatus.Alive alive2 = new MemberStatus.Alive(ADDRESS, 5);

		MemberStatus result = alive1.merge(alive2);

		assertThat(result).isEqualTo(new MemberStatus.Alive(ADDRESS, 5));
	}

	@Test
	void testAliveMergeWithSuspected_suspectedHigherIncarnation() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 3);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 5);

		MemberStatus result = alive.merge(suspected);

		assertThat(result).isEqualTo(suspected);
	}

	@Test
	void testAliveMergeWithSuspected_suspectedEqualIncarnation() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 5);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 5);

		MemberStatus result = alive.merge(suspected);

		assertThat(result).isEqualTo(suspected);
	}

	@Test
	void testAliveMergeWithSuspected_suspectedLowerIncarnation() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 10);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 3);

		MemberStatus result = alive.merge(suspected);

		assertThat(result).isEqualTo(alive);
	}

	@Test
	void testAliveMergeWithConfirmed() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 100);
		MemberStatus.Confirmed confirmed = new MemberStatus.Confirmed(ADDRESS, 1);

		MemberStatus result = alive.merge(confirmed);

		// Confirmed always wins over Alive
		assertThat(result).isEqualTo(confirmed);
	}

	@Test
	void testSuspectedMergeWithAlive_aliveHigherIncarnation() {
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 3);
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 5);

		MemberStatus result = suspected.merge(alive);

		assertThat(result).isEqualTo(alive);
	}

	@Test
	void testSuspectedMergeWithAlive_aliveEqualIncarnation() {
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 5);
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 5);

		MemberStatus result = suspected.merge(alive);

		assertThat(result).isEqualTo(alive);
	}

	@Test
	void testSuspectedMergeWithAlive_aliveLowerIncarnation() {
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 10);
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 3);

		MemberStatus result = suspected.merge(alive);

		assertThat(result).isEqualTo(suspected);
	}

	@Test
	void testSuspectedMergeWithSuspected_higherIncarnation() {
		MemberStatus.Suspected suspected1 = new MemberStatus.Suspected(ADDRESS, 2);
		MemberStatus.Suspected suspected2 = new MemberStatus.Suspected(ADDRESS, 7);

		MemberStatus result = suspected1.merge(suspected2);

		assertThat(result).isEqualTo(new MemberStatus.Suspected(ADDRESS, 7));
	}

	@Test
	void testSuspectedMergeWithSuspected_lowerIncarnation() {
		MemberStatus.Suspected suspected1 = new MemberStatus.Suspected(ADDRESS, 10);
		MemberStatus.Suspected suspected2 = new MemberStatus.Suspected(ADDRESS, 4);

		MemberStatus result = suspected1.merge(suspected2);

		assertThat(result).isEqualTo(new MemberStatus.Suspected(ADDRESS, 10));
	}

	@Test
	void testSuspectedMergeWithSuspected_sameIncarnation() {
		MemberStatus.Suspected suspected1 = new MemberStatus.Suspected(ADDRESS, 5);
		MemberStatus.Suspected suspected2 = new MemberStatus.Suspected(ADDRESS, 5);

		MemberStatus result = suspected1.merge(suspected2);

		assertThat(result).isEqualTo(new MemberStatus.Suspected(ADDRESS, 5));
	}

	@Test
	void testSuspectedMergeWithConfirmed() {
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 100);
		MemberStatus.Confirmed confirmed = new MemberStatus.Confirmed(ADDRESS, 1);

		MemberStatus result = suspected.merge(confirmed);

		// Confirmed always wins over Suspected
		assertThat(result).isEqualTo(confirmed);
	}

	@Test
	void testConfirmedMergeWithAlive() {
		MemberStatus.Confirmed confirmed = new MemberStatus.Confirmed(ADDRESS, 1);
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 100);

		MemberStatus result = confirmed.merge(alive);

		// Confirmed always wins over Alive
		assertThat(result).isEqualTo(confirmed);
	}

	@Test
	void testConfirmedMergeWithSuspected() {
		MemberStatus.Confirmed confirmed = new MemberStatus.Confirmed(ADDRESS, 1);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 100);

		MemberStatus result = confirmed.merge(suspected);

		// Confirmed always wins over Suspected
		assertThat(result).isEqualTo(confirmed);
	}

	@Test
	void testConfirmedMergeWithConfirmed_higherIncarnation() {
		MemberStatus.Confirmed confirmed1 = new MemberStatus.Confirmed(ADDRESS, 2);
		MemberStatus.Confirmed confirmed2 = new MemberStatus.Confirmed(ADDRESS, 8);

		MemberStatus result = confirmed1.merge(confirmed2);

		assertThat(result).isEqualTo(new MemberStatus.Confirmed(ADDRESS, 8));
	}

	@Test
	void testConfirmedMergeWithConfirmed_lowerIncarnation() {
		MemberStatus.Confirmed confirmed1 = new MemberStatus.Confirmed(ADDRESS, 10);
		MemberStatus.Confirmed confirmed2 = new MemberStatus.Confirmed(ADDRESS, 3);

		MemberStatus result = confirmed1.merge(confirmed2);

		assertThat(result).isEqualTo(new MemberStatus.Confirmed(ADDRESS, 10));
	}

	@Test
	void testConfirmedMergeWithConfirmed_sameIncarnation() {
		MemberStatus.Confirmed confirmed1 = new MemberStatus.Confirmed(ADDRESS, 5);
		MemberStatus.Confirmed confirmed2 = new MemberStatus.Confirmed(ADDRESS, 5);

		MemberStatus result = confirmed1.merge(confirmed2);

		assertThat(result).isEqualTo(new MemberStatus.Confirmed(ADDRESS, 5));
	}

	@Test
	void testMergeIsSymmetric_aliveAndSuspected() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 5);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 3);

		MemberStatus result1 = alive.merge(suspected);
		MemberStatus result2 = suspected.merge(alive);

		// Both should result in Alive since alive has higher incarnation
		assertThat(result1).isEqualTo(alive);
		assertThat(result2).isEqualTo(alive);
	}

	@Test
	void testMergeChain() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 1);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 2);
		MemberStatus.Confirmed confirmed = new MemberStatus.Confirmed(ADDRESS, 3);

		MemberStatus intermediate = alive.merge(suspected);
		assertThat(intermediate).isEqualTo(suspected);

		MemberStatus finalStatus = intermediate.merge(confirmed);
		assertThat(finalStatus).isEqualTo(confirmed);
	}

	@Test
	void testIncarnationZero() {
		MemberStatus.Alive alive = new MemberStatus.Alive(ADDRESS, 0);
		MemberStatus.Suspected suspected = new MemberStatus.Suspected(ADDRESS, 0);

		MemberStatus result = alive.merge(suspected);

		// With equal incarnation, suspected should win
		assertThat(result).isEqualTo(suspected);
	}

	@Test
	void testNegativeIncarnation() {
		// Testing edge case with negative incarnations (if allowed)
		MemberStatus.Alive alive1 = new MemberStatus.Alive(ADDRESS, -5);
		MemberStatus.Alive alive2 = new MemberStatus.Alive(ADDRESS, -10);

		MemberStatus result = alive1.merge(alive2);

		assertThat(result).isEqualTo(new MemberStatus.Alive(ADDRESS, -5));
	}
}
