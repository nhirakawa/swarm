package com.github.nhirakawa.swarm.protocol.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonSubTypes({
    @Type(name = "PING", value = PingMessage.class),
    @Type(name = "PING_REQUEST", value = PingRequestMessage.class),
    @Type(name = "PING_ACK", value = PingAckMessage.class)
})
public interface BaseSwarmMessage {

  SwarmMessageType getType();
  SwarmNode getSender();

}
