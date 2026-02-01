package com.github.nhirakawa.swarm.protocol.model.internal;

public sealed interface StateMachineResponse
  permits PingRequestResponse, PingAckResponse {}
