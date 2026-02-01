package com.github.nhirakawa.swarm.protocol.model.serde.header;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;

public record MessageHeader(
  MessageVersion messageVersion,
  SwarmMessageType type,
  Compression compression,
  Serialization serialization
) {}
