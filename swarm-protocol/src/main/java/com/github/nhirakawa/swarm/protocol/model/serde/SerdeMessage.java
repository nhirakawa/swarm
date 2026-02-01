package com.github.nhirakawa.swarm.protocol.model.serde;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;

public sealed interface SerdeMessage
  permits PingAckSerdeMessage, PingRequestSerdeMessage {
  SwarmAddress fromAddress();
  SwarmAddress toAddress();
}
