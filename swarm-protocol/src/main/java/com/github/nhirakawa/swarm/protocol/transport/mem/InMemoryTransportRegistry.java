package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central registry for in-memory transport instances.
 * Enables message routing between nodes in a single-process, multi-node simulation.
 */
@ThreadSafe
public class InMemoryTransportRegistry {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryTransportRegistry.class
  );

  private final ConcurrentHashMap<SwarmAddress, InMemoryTransport> registry;

  public InMemoryTransportRegistry() {
    this.registry = new ConcurrentHashMap<>();
  }

  /**
   * Register a transport instance for the given address.
   *
   * @param address the swarm address of the node
   * @param transport the transport instance for that node
   * @throws IllegalStateException if address is already registered
   */
  public void register(SwarmAddress address, InMemoryTransport transport) {
    InMemoryTransport previous = registry.putIfAbsent(address, transport);
    if (previous != null) {
      throw new IllegalStateException(
        String.format(
          "Transport already registered for address %s",
          address
        )
      );
    }
    LOG.info("Registered transport for address: {}", address);
  }

  /**
   * Deregister a transport instance for the given address.
   *
   * @param address the swarm address to deregister
   */
  public void deregister(SwarmAddress address) {
    InMemoryTransport removed = registry.remove(address);
    if (removed != null) {
      LOG.info("Deregistered transport for address: {}", address);
    } else {
      LOG.warn("Attempted to deregister non-existent address: {}", address);
    }
  }

  /**
   * Lookup a transport instance by address.
   *
   * @param address the swarm address to lookup
   * @return the transport instance if registered, empty otherwise
   */
  public Optional<InMemoryTransport> lookup(SwarmAddress address) {
    return Optional.ofNullable(registry.get(address));
  }

  /**
   * Get the number of registered transports.
   * Primarily useful for testing.
   *
   * @return the count of registered transports
   */
  public int size() {
    return registry.size();
  }

  /**
   * Clear all registered transports.
   * Primarily useful for testing.
   */
  public void clear() {
    registry.clear();
    LOG.info("Cleared all registered transports");
  }
}
