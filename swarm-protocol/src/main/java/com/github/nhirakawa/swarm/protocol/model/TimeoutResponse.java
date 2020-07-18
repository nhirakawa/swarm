package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.swarm.protocol.config.SwarmNodeModel;

import org.derive4j.Data;

@Data
public abstract class TimeoutResponse {

  public interface Cases<R> {
    R empty();
    R ping(String protocolId, SwarmNodeModel swarmNode);
    R proxy(String protocolId, ProxyTargetsModel proxyTargets);
  }

  public abstract <R> R match(Cases<R> cases);
}
