package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central registry for in-memory transport instances.
 * Provides lookup for message delivery in a single-process, multi-node simulation.
 */
@ThreadSafe
public class InMemoryTransportRegistry {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryTransportRegistry.class
  );

  @GuardedBy("this")
  private final ConcurrentMap<SwarmAddress, InMemoryTransport> registry;
  @GuardedBy("this")
  private final ConcurrentMap<SwarmAddress, byte[]> resolvedAddresses;

  public InMemoryTransportRegistry() {
    this.registry = new ConcurrentHashMap<>();
    this.resolvedAddresses = new ConcurrentHashMap<>();
  }

  /**
   * Register a transport instance for the given address.
   *
   * @param address the swarm address of the node
   * @param transport the transport instance for that node
   * @throws IllegalStateException if address is already registered
   */
  synchronized void register(SwarmAddress address, InMemoryTransport transport) throws UnknownHostException {
    InMemoryTransport previous = registry.putIfAbsent(address, transport);
    if (previous != null) {
      throw new IllegalStateException(
        String.format(
          "Transport already registered for address %s",
          address
        )
      );
    }
    InetAddress inetAddress = InetAddress.getByName(address.address());
    resolvedAddresses.put(address, inetAddress.getAddress());
    LOG.info("Registered transport for address: {}", address);
  }

  /**
   * Deregister a transport instance for the given address.
   *
   * @param address the swarm address to deregister
   */
  synchronized void deregister(SwarmAddress address) {
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
  synchronized Optional<InMemoryTransport> lookup(SwarmAddress address) {
    return Optional.ofNullable(registry.get(address));
  }

  synchronized Optional<byte[]> resolve(SwarmAddress address) {
    return Optional.ofNullable(resolvedAddresses.get(address));
  }

  /**
   * Get the number of registered transports.
   * Primarily useful for testing.
   *
   * @return the count of registered transports
   */
  synchronized int size() {
    return registry.size();
  }

  synchronized ImmutableSet<SwarmAddress> keys() {
    return ImmutableSet.copyOf(registry.keySet());
  }

  /**
   * Clear all registered transports.
   * Primarily useful for testing.
   */
  synchronized void clear() {
    registry.clear();
    resolvedAddresses.clear();
    LOG.info("Cleared all registered transports");
  }
}
