package com.github.nhirakawa.swarm.protocol.model.serde;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

public record PingAckSerdeMessage(
  SwarmAddress fromAddress,
  SwarmAddress toAddress,
  Optional<SwarmAddress> proxyFor,
  String protocolPeriodId,
  String uniqueMessageId
)
  implements SerdeMessage {}
