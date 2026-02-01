package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageReceiver;
import com.github.nhirakawa.swarm.protocol.transport.SwarmMessageSender;
import com.github.nhirakawa.swarm.protocol.transport.SwarmTransport;
import com.google.common.util.concurrent.AbstractIdleService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * In-memory implementation of SwarmTransport for single-process, multi-node simulation.
 * Automatically registers/deregisters with the transport registry on start/stop.
 */
public class InMemoryTransport
  extends AbstractIdleService
  implements SwarmTransport {

  private static final Logger LOG = LogManager.getLogger(
    InMemoryTransport.class
  );

  private final SwarmAddress localAddress;
  private final InMemoryTransportRegistry registry;
  private final InMemoryMessageSender sender;
  private final InMemoryMessageReceiver receiver;

  public InMemoryTransport(
    SwarmAddress localAddress,
    InMemoryTransportRegistry registry,
    int queueCapacity
  ) {
    this.localAddress = localAddress;
    this.registry = registry;
    this.receiver = new InMemoryMessageReceiver(queueCapacity);
    this.sender = new InMemoryMessageSender(localAddress, registry);
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting in-memory transport for address: {}", localAddress);
    registry.register(localAddress, this);
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Shutting down in-memory transport for address: {}", localAddress);
    registry.deregister(localAddress);
  }

  @Override
  public SwarmMessageReceiver receiver() {
    return receiver;
  }

  @Override
  public SwarmMessageSender sender() {
    return sender;
  }

  public SwarmAddress getLocalAddress() {
    return localAddress;
  }
}
