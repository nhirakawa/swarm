package com.github.nhirakawa.swarm.protocol.model.internal;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.MemberStatus;
import java.util.List;

public record DiscoveryResponseResponse(
  SwarmAddress target,
  List<MemberStatus> memberList
) implements StateMachineResponse {}
