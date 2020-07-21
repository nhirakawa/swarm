package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.config.SwarmNodeModel;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface ProxyTargetModel {
  SwarmNodeModel getTargetNode();
  SwarmNodeModel getProxyNode();
}
