package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GaussianLatencyDistributionTest {

	@Test
	void testSample_isNonNegative() {
		// Use a small mean with large stddev to exercise the floor-at-zero behavior
		GaussianLatencyDistribution dist = new GaussianLatencyDistribution(
			Duration.ofMillis(1),
			Duration.ofMillis(100)
		);

		for (int i = 0; i < 100; i++) {
			assertThat(dist.sample()).isGreaterThanOrEqualTo(Duration.ZERO);
		}
	}

	@Test
	void testSample_clustersAroundMean() {
		Duration mean = Duration.ofMillis(100);
		Duration stddev = Duration.ofMillis(5);
		GaussianLatencyDistribution dist = new GaussianLatencyDistribution(
			mean,
			stddev
		);

		long totalNanos = 0;
		int iterations = 1000;
		for (int i = 0; i < iterations; i++) {
			totalNanos += dist.sample().toNanos();
		}

		Duration average = Duration.ofNanos(totalNanos / iterations);
		// Average should be within 3 stddevs of mean
		assertThat(average).isBetween(
			Duration.ofMillis(85),
			Duration.ofMillis(115)
		);
	}

	@Test
	void testSample_producesVariableResults() {
		GaussianLatencyDistribution dist = new GaussianLatencyDistribution(
			Duration.ofMillis(100),
			Duration.ofMillis(20)
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
	void testConstructor_throwsWhenMeanIsNegative() {
		assertThatThrownBy(() ->
			new GaussianLatencyDistribution(
				Duration.ofMillis(-1),
				Duration.ofMillis(10)
			)
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testConstructor_throwsWhenStddevIsZero() {
		assertThatThrownBy(() ->
			new GaussianLatencyDistribution(Duration.ofMillis(100), Duration.ZERO)
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testConstructor_throwsWhenStddevIsNegative() {
		assertThatThrownBy(() ->
			new GaussianLatencyDistribution(
				Duration.ofMillis(100),
				Duration.ofMillis(-1)
			)
		).isInstanceOf(IllegalArgumentException.class);
	}
}
