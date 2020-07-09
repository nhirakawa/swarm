package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.swarm.protocol.config.AbstractSwarmNode;
import org.derive4j.Data;

@Data
public abstract class PingResponse {

  interface Cases<R> {
    R ack();
    R proxy(AbstractSwarmNode swarmNode);
  }

  public abstract <R> R match(Cases<R> cases);
}