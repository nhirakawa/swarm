package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface MemberStatusUpdateModel {
  MemberStatus getNewMemberStatus();
  SwarmNode getSwarmNode();
}
