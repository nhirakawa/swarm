package com.github.nhirakawa.swarm.protocol.config;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.Set;
import org.immutables.value.Value;
import org.jspecify.annotations.NonNull;

@Value.Immutable
@ImmutableStyle
public abstract class SwarmConfigModel {

	@NonNull
	public abstract SwarmAddress getLocalAddress();

	@NonNull
	public abstract SwarmAddress getMulticastAddress();

	public abstract Duration getProtocolPeriod();

	public abstract Duration getMessageTimeout();

	public abstract Duration getProtocolTick();

	public abstract int getFailureSubGroup();

	@Value.Default
	public Duration getSuspicionTimeout() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() * 3);
	}

	@Value.Default
	public Duration getProtocolPeriodJitter() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() / 10);
	}

	@Value.Default
	public Duration getMessageTimeoutJitter() {
		return Duration.ofMillis(getMessageTimeout().toMillis() / 10);
	}

	/** How often a joined node broadcasts a discovery request, so healed partitions find each other. */
	@Value.Default
	public Duration getRediscoveryInterval() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() * 6);
	}

	/** How long a confirmed member stays in the registry before it is reaped. */
	@Value.Default
	public Duration getConfirmedRetention() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() * 6);
	}

	/** The most times a node may refute its own confirmation within {@link #getRefutationWindow()}. */
	@Value.Default
	public int getMaxRefutations() {
		return 3;
	}

	@Value.Default
	public Duration getRefutationWindow() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() * 30);
	}

	@Value.Default
	public Set<SwarmAddress> getInitialGroup() {
		return Set.of();
	}

	@Value.Default
	public boolean isDiscoveryEnabled() {
		return true;
	}

	@Value.Default
	public Duration getDiscoveryInitialRetryDelay() {
		return Duration.ofMillis(500);
	}

	@Value.Default
	public Duration getDiscoveryMaxRetryDelay() {
		return Duration.ofSeconds(30);
	}

	@Value.Default
	public double getDiscoveryBackoffMultiplier() {
		return 2.0;
	}

	@Value.Check
	public void check() {
		Preconditions.checkArgument(
			getProtocolPeriod().compareTo(getMessageTimeout()) > 0,
			"Expected protocol period (%s) to be longer than message timeout (%s)",
			getProtocolPeriod(),
			getMessageTimeout()
		);

		Preconditions.checkArgument(
			getRediscoveryInterval().compareTo(Duration.ZERO) > 0,
			"Rediscovery interval must be positive: %s",
			getRediscoveryInterval()
		);

		Preconditions.checkArgument(
			getConfirmedRetention().compareTo(Duration.ZERO) >= 0,
			"Confirmed retention must not be negative: %s",
			getConfirmedRetention()
		);

		Preconditions.checkArgument(
			getMaxRefutations() >= 0,
			"Max refutations must not be negative: %s",
			getMaxRefutations()
		);

		Preconditions.checkArgument(
			getRefutationWindow().compareTo(Duration.ZERO) > 0,
			"Refutation window must be positive: %s",
			getRefutationWindow()
		);

		Preconditions.checkArgument(
			getDiscoveryInitialRetryDelay().compareTo(Duration.ZERO) > 0,
			"Discovery initial retry delay must be positive: %s",
			getDiscoveryInitialRetryDelay()
		);

		Preconditions.checkArgument(
			getDiscoveryMaxRetryDelay().compareTo(getDiscoveryInitialRetryDelay()) >=
				0,
			"Discovery max retry delay (%s) must be >= initial retry delay (%s)",
			getDiscoveryMaxRetryDelay(),
			getDiscoveryInitialRetryDelay()
		);

		Preconditions.checkArgument(
			getDiscoveryBackoffMultiplier() >= 1.0,
			"Discovery backoff multiplier must be >= 1.0: %s",
			getDiscoveryBackoffMultiplier()
		);
	}
}
