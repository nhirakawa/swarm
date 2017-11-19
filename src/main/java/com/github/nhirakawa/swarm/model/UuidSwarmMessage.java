package com.github.nhirakawa.swarm.model;

import java.util.UUID;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize
public interface UuidSwarmMessage extends BaseSwarmMessage {

  UUID getUuid();

}
