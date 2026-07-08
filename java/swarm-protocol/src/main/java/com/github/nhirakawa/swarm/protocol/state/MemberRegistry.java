package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ComparisonChain;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * The source of truth for group membership
 * <p>
 * Combines group membership and gossip counting in a single structure
 * <p>
 * This class is **not** thread-safe.
 * Thread safety must be handled by a higher abstraction (like {@link SwarmStateMachine}).
 */
@NotThreadSafe
class MemberRegistry {

	private final Ticker ticker;
	private final Map<SwarmAddress, Counted> registry = new HashMap<>();
	private final NavigableSet<Counted> sortedByGossipCount = new TreeSet<>();
	private final Queue<SwarmAddress> pingSequence = new ArrayDeque<>();
	private final Map<SwarmAddress, Stopwatch> suspicionStopwatches =
		new HashMap<>();

	MemberRegistry() {
		this(Set.of(), Ticker.systemTicker());
	}

	@VisibleForTesting
	MemberRegistry(Set<? extends SwarmAddress> initialGroup) {
		this(initialGroup, Ticker.systemTicker());
	}

	@VisibleForTesting
	MemberRegistry(Set<? extends SwarmAddress> initialGroup, Ticker ticker) {
		this.ticker = ticker;
		for (SwarmAddress swarmAddress : initialGroup) {
			var memberStatus = MemberStatus.alive(swarmAddress, 0);
			var counted = Counted.initial(swarmAddress, memberStatus);
			registry.put(swarmAddress, counted);
			sortedByGossipCount.add(counted);
			pingSequence.add(swarmAddress);
		}
	}

	List<MemberStatus> getMemberStatuses() {
		return registry.values().stream().map(Counted::memberStatus).toList();
	}

	Optional<MemberStatus> get(SwarmAddress swarmAddress) {
		return Optional.ofNullable(registry.get(swarmAddress)).map(
			Counted::memberStatus
		);
	}

	int size() {
		return registry.size();
	}

	void put(SwarmAddress swarmAddress, MemberStatus memberStatus) {
		Counted oldCounted = registry.get(swarmAddress);
		if (oldCounted != null) {
			sortedByGossipCount.remove(oldCounted);
		}

		Counted newCounted = Counted.initial(swarmAddress, memberStatus);
		if (oldCounted != null) {
			newCounted = oldCounted.merge(newCounted);
		}

		registry.put(swarmAddress, newCounted);
		sortedByGossipCount.add(newCounted);

		if (oldCounted == null && memberStatus.isEligibleForPing()) {
			pingSequence.add(swarmAddress);
		}

		boolean wasAlreadySuspected =
			oldCounted != null &&
			oldCounted.memberStatus() instanceof MemberStatus.Suspected;
		boolean isNowSuspected =
			newCounted.memberStatus() instanceof MemberStatus.Suspected;

		if (!wasAlreadySuspected && isNowSuspected) {
			suspicionStopwatches.put(swarmAddress, Stopwatch.createStarted(ticker));
		} else if (wasAlreadySuspected && !isNowSuspected) {
			suspicionStopwatches.remove(swarmAddress);
		}
	}

	void promoteExpiredSuspicions(Duration timeout) {
		List<SwarmAddress> expired = suspicionStopwatches
			.entrySet()
			.stream()
			.filter(e -> e.getValue().elapsed().toNanos() >= timeout.toNanos())
			.map(Map.Entry::getKey)
			.toList();

		for (SwarmAddress address : expired) {
			long incarnation = get(address).map(MemberStatus::incarnation).orElse(0L);
			put(address, MemberStatus.confirmed(address, incarnation));
		}
	}

	SwarmAddress getPingTarget() {
		while (!pingSequence.isEmpty()) {
			SwarmAddress candidate = pingSequence.poll();
			Counted counted = registry.get(candidate);
			if (counted != null && counted.memberStatus.isEligibleForPing()) {
				return candidate;
			}
		}
		// Sequence exhausted — build a new shuffled sequence from all eligible nodes
		List<SwarmAddress> eligible = registry
			.entrySet()
			.stream()
			.filter(e -> e.getValue().memberStatus.isEligibleForPing())
			.map(Map.Entry::getKey)
			.collect(Collectors.toCollection(ArrayList::new));
		Collections.shuffle(eligible);
		pingSequence.addAll(eligible);
		return pingSequence.poll();
	}

	/**
	 * Gets the top n member statuses with the lowest gossip counts for inclusion in gossip messages,
	 * and atomically increments their gossip counts.
	 * <p>
	 * This prioritizes spreading newer status updates that haven't been widely disseminated yet.
	 *
	 * @param maxCount the maximum number of member statuses to include
	 * @return a list of members with their statuses, ordered by ascending gossip count
	 */
	List<MemberStatus> getGossipPayload(int maxCount) {
		List<Counted> toGossip = sortedByGossipCount
			.stream()
			.limit(maxCount)
			.toList();

		// Atomically increment gossip counts for the selected members
		for (Counted counted : toGossip) {
			sortedByGossipCount.remove(counted);
			Counted updated = new Counted(
				counted.swarmAddress,
				counted.memberStatus,
				counted.gossipCount + 1
			);
			registry.put(counted.swarmAddress, updated);
			sortedByGossipCount.add(updated);
		}

		return toGossip.stream().map(Counted::memberStatus).toList();
	}

	void evictConfirmedNodes() {
		int threshold = Math.max(
			(int) Math.ceil(Math.log(registry.size() + 1) / Math.log(2)),
			3
		);

		List<SwarmAddress> toEvict = registry
			.entrySet()
			.stream()
			.filter(
				e -> e.getValue().memberStatus() instanceof MemberStatus.Confirmed
			)
			.filter(e -> e.getValue().gossipCount() >= threshold)
			.map(Map.Entry::getKey)
			.toList();

		for (SwarmAddress address : toEvict) {
			Counted counted = registry.remove(address);
			sortedByGossipCount.remove(counted);
			pingSequence.remove(address);
			suspicionStopwatches.remove(address);
		}
	}

	Set<SwarmAddress> getFailureSubGroup(int size, SwarmAddress proxyFor) {
		List<SwarmAddress> potentialTargets = registry
			.keySet()
			.stream()
			.filter(target -> !target.equals(proxyFor))
			.collect(Collectors.toCollection(ArrayList::new));

		if (size >= potentialTargets.size()) {
			return Set.copyOf(potentialTargets);
		}

		Collections.shuffle(potentialTargets);

		return Set.copyOf(potentialTargets.subList(0, size));
	}

	private record Counted(
		SwarmAddress swarmAddress,
		MemberStatus memberStatus,
		int gossipCount
	) implements Comparable<Counted> {
		static Counted initial(
			SwarmAddress swarmAddress,
			MemberStatus memberStatus
		) {
			return new Counted(swarmAddress, memberStatus, 0);
		}

		Counted merge(Counted other) {
			MemberStatus newMemberStatus = memberStatus.merge(other.memberStatus);
			if (newMemberStatus.equals(memberStatus)) {
				return this;
			} else {
				return initial(swarmAddress, newMemberStatus);
			}
		}

		@Override
		public int compareTo(Counted other) {
			return ComparisonChain.start()
				.compare(gossipCount, other.gossipCount)
				.compare(
					swarmAddress,
					other.swarmAddress,
					Comparator.comparing(SwarmAddress::asString)
				)
				.result();
		}
	}
}
