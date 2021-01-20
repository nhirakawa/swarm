package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.protocol.MemberStatus;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface MemberStatusUpdateModel {
  MemberStatus getNewMemberStatus();
  SwarmNode getSwarmNode();
}
