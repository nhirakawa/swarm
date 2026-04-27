package com.github.nhirakawa.swarm.protocol.transport.mem;

import java.time.Duration;

public interface LatencyDistribution {
  Duration sample();
}
