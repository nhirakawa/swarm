package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;

/**
 * Represents a message on the wire with routing information and header.
 * Payload is serialized bytes ready for transmission.
 */
public record WireMessage(
  SwarmAddress source,
  SwarmAddress target,
  MessageHeader header,
  byte[] payload
) {}
