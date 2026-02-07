package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
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

  private static final int RECEIVER_QUEUE_CAPACITY = 1000;

  public InMemoryTransport(
    SwarmAddress localAddress,
    InMemoryTransportRegistry registry,
    NetworkSimulator networkSimulator
  ) {
    this.localAddress = localAddress;
    this.registry = registry;
    this.receiver = new InMemoryMessageReceiver(RECEIVER_QUEUE_CAPACITY);
    this.sender = new InMemoryMessageSender(localAddress, networkSimulator);
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
  public InMemoryMessageReceiver receiver() {
    return receiver;
  }

  @Override
  public InMemoryMessageSender sender() {
    return sender;
  }

  public SwarmAddress getLocalAddress() {
    return localAddress;
  }
}
