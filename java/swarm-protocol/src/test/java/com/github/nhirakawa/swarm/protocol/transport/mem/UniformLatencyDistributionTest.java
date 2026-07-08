package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UniformLatencyDistributionTest {

	@Test
	void testSample_returnsValueInRange() {
		Duration min = Duration.ofMillis(10);
		Duration max = Duration.ofMillis(50);
		UniformLatencyDistribution dist = new UniformLatencyDistribution(min, max);

		for (int i = 0; i < 100; i++) {
			Duration sample = dist.sample();
			assertThat(sample).isBetween(min, max);
		}
	}

	@Test
	void testSample_producesVariableResults() {
		UniformLatencyDistribution dist = new UniformLatencyDistribution(
			Duration.ofMillis(1),
			Duration.ofMillis(1000)
		);

		Duration first = dist.sample();
		boolean foundDifferent = false;
		for (int i = 0; i < 50; i++) {
			if (!dist.sample().equals(first)) {
				foundDifferent = true;
				break;
			}
		}

		assertThat(foundDifferent).as("should produce different samples").isTrue();
	}

	@Test
	void testConstructor_throwsWhenMinIsNegative() {
		assertThatThrownBy(() ->
			new UniformLatencyDistribution(
				Duration.ofMillis(-1),
				Duration.ofMillis(10)
			)
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testConstructor_throwsWhenMinEqualsMax() {
		assertThatThrownBy(() ->
			new UniformLatencyDistribution(
				Duration.ofMillis(10),
				Duration.ofMillis(10)
			)
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testConstructor_throwsWhenMinExceedsMax() {
		assertThatThrownBy(() ->
			new UniformLatencyDistribution(
				Duration.ofMillis(50),
				Duration.ofMillis(10)
			)
		).isInstanceOf(IllegalArgumentException.class);
	}
}
