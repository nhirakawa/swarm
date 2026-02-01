package com.github.nhirakawa.swarm.protocol.serde;

import com.github.nhirakawa.swarm.protocol.model.serde.header.MessageHeader;
import com.google.common.primitives.UnsignedBytes;

public class HeaderSerializer {

  public HeaderSerializer() {}

  public byte[] serialize(MessageHeader header) {
    return new byte[] {
      UnsignedBytes.checkedCast(header.messageVersion().value()),
      UnsignedBytes.checkedCast(header.type().value()),
      UnsignedBytes.checkedCast(header.compression().value()),
      UnsignedBytes.checkedCast(header.serialization().value()),
    };
  }
}
