package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.model.internal.StateMachineMessage;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;

/**
 * Represents a message on the wire with routing information and header.
 */
public record WireMessage(
  SwarmAddress source,
  SwarmAddress target,
  MessageHeader header,
  StateMachineMessage payload
) {}
