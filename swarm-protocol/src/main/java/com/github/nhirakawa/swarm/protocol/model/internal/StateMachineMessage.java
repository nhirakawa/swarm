package com.github.nhirakawa.swarm.protocol.model.internal;

public sealed interface StateMachineMessage
  permits InboundPingAck,
    InboundPingRequest,
    InboundDiscoveryRequest,
    InboundDiscoveryResponse {}
