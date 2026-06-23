package com.github.nhirakawa.swarm.protocol.transport;

import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import com.google.common.util.concurrent.Service;

public interface SwarmTransport extends Service {
  SwarmMessageReceiver receiver();
  SwarmMessageSender sender();
  SwarmAddress getMulticastAddress();
}
