package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.swarm.protocol.config.AbstractSwarmNode;
import java.time.Instant;
import org.derive4j.Data;

@Data
public abstract class PingAck {

  interface Cases<R> {
    R ack(Instant timestamp);
    R proxy(AbstractSwarmNode swarmNode);
    R invalidProtocolPeriod();
    R noOutstandingPingRequest();
    R invalidSender();
  }

  public abstract <R> R match(Cases<R> cases);
}