package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import java.time.Duration;

/**
 * Configuration for network simulation including latency and packet loss.
 */
public interface NetworkSimulationConfig {
  /**
   * Sample a latency duration for a message between source and target.
   *
   * @param source the source address
   * @param target the target address
   * @return the latency duration to apply
   */
  Duration sampleLatency(SwarmAddress source, SwarmAddress target);

  /**
   * Determine if a message should be dropped when sent.
   *
   * @param source the source address
   * @param target the target address
   * @return true if the message should be dropped
   */
  boolean shouldDropOnSend(SwarmAddress source, SwarmAddress target);

  /**
   * Determine if a message should be dropped while in transit.
   *
   * @param source the source address
   * @param target the target address
   * @return true if the message should be dropped
   */
  boolean shouldDropInTransit(SwarmAddress source, SwarmAddress target);
}
