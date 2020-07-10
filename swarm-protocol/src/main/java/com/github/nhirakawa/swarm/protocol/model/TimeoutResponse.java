package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.swarm.protocol.config.AbstractSwarmNode;
import java.util.List;
import org.derive4j.Data;

@Data
public abstract class TimeoutResponse {

  public interface Cases<R> {
    R empty();
    R ping(AbstractSwarmNode swarmNode);
    R proxy(ProxyTargetsModel proxyTargets);
  }

  public abstract <R> R match(Cases<R> cases);
}
