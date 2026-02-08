package com.github.nhirakawa.swarm.runner.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.runner.admin.AdminConfig;
import com.google.common.base.Preconditions;
import org.immutables.value.Value;

import java.time.Duration;
import java.util.Optional;

@Value.Immutable
@ImmutableStyle
public interface LocalSwarmConfigModel {
	int getNumberOfNodes();

	Duration getProtocolPeriod();

	Duration getMessageTimeout();

	Duration getProtocolTick();

	int getFailureSubGroup();

	@Value.Default
	default Duration getProtocolPeriodJitter() {
		return Duration.ofMillis(getProtocolPeriod().toMillis() / 10);
	}

	@Value.Default
	default Duration getMessageTimeoutJitter() {
		return Duration.ofMillis(getMessageTimeout().toMillis() / 10);
	}

	Optional<AdminConfig> getAdminConfig();

	@Value.Check
	default void check() {
		Preconditions.checkArgument(
				getProtocolPeriod().toNanos() > getMessageTimeout().toNanos(),
				"Expected protocol period (%s) to be longer than message timeout (%s)",
				getProtocolPeriod(),
				getMessageTimeout()
		);
	}
}
