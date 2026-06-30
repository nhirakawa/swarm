package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.google.common.base.Preconditions;
import java.time.Duration;
import org.apache.commons.math3.distribution.NormalDistribution;

public class GaussianLatencyDistribution implements LatencyDistribution {

	private final NormalDistribution distribution;

	public GaussianLatencyDistribution(
		Duration mean,
		Duration standardDeviation
	) {
		Preconditions.checkArgument(
			!mean.isNegative(),
			"mean must be non-negative"
		);
		Preconditions.checkArgument(
			!standardDeviation.isNegative() && !standardDeviation.isZero(),
			"standardDeviation must be positive"
		);
		this.distribution = new NormalDistribution(
			mean.toNanos(),
			standardDeviation.toNanos()
		);
	}

	@Override
	public synchronized Duration sample() {
		return Duration.ofNanos(Math.max(0, (long) distribution.sample()));
	}
}
