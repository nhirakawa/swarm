package com.github.nhirakawa.swarm.protocol.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import java.util.UUID;
import org.immutables.value.Value;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = As.EXISTING_PROPERTY
)
@JsonSubTypes(
  {
    @Type(name = "PING", value = PingMessage.class),
    @Type(name = "PING_REQUEST", value = PingRequestMessage.class),
    @Type(name = "PING_ACK", value = PingAckMessage.class)
  }
)
public interface BaseSwarmMessage {
  SwarmMessageType getType();
  SwarmNode getSender();
  String getProtocolPeriodId();

  @Value.Default
  default String getUniqueMessageId() {
    return UUID.randomUUID().toString();
  }
}
