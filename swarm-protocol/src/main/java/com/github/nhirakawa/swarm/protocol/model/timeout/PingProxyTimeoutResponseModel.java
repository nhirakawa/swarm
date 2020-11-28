package com.github.nhirakawa.swarm.protocol.model.timeout;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.ProxyTarget;
import com.github.nhirakawa.swarm.protocol.model.ProxyTargets;
import com.google.common.base.Preconditions;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public abstract class PingProxyTimeoutResponseModel implements TimeoutResponse {

  public abstract String getProtocolId();

  public abstract ProxyTargets getProxyTargets();
}
