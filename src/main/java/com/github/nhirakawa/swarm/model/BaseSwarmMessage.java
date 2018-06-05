package com.github.nhirakawa.swarm.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonSubTypes(@Type(name = "UUID", value = UuidSwarmMessage.class))
public interface BaseSwarmMessage {

  SwarmMessageType getType();

}
