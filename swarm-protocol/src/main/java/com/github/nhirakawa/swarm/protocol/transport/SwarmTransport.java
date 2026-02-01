package com.github.nhirakawa.swarm.protocol.transport;

import com.google.common.util.concurrent.Service;

public interface SwarmTransport extends Service {
  SwarmMessageReceiver receiver();
  SwarmMessageSender sender();
}
