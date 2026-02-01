package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.SwarmMessageType;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Compression;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageVersion;
import com.github.nhirakawa.swarm.protocol.model.serde.header.Serialization;
import com.google.common.base.Preconditions;

public class HeaderDeserializer {

  public HeaderDeserializer() {}

  public MessageHeader deserialize(byte[] bytes) {
    Preconditions.checkArgument(
      bytes.length == 4,
      "Expected 4 bytes, got %s",
      bytes.length
    );

    MessageVersion messageVersion = MessageVersion.parse(bytes[0]);
    SwarmMessageType messageType = SwarmMessageType.parse(bytes[1]);
    Compression compression = Compression.parse(bytes[2]);
    Serialization serialization = Serialization.parse(bytes[3]);

    return new MessageHeader(
      messageVersion,
      messageType,
      compression,
      serialization
    );
  }
}
