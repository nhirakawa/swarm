package com.github.nhirakawa.swarm.protocol.transport.mem;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class InMemorySwarmAddressSerializer extends StdSerializer<InMemorySwarmAddress> {

  public InMemorySwarmAddressSerializer() {
    super(InMemorySwarmAddress.class);
  }

  @Override
  public void serialize(InMemorySwarmAddress value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeString(value.address());
  }
}
