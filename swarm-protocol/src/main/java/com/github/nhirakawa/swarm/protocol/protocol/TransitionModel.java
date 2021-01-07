package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.BaseSwarmMessage;
import com.github.nhirakawa.swarm.protocol.state.SwarmProtocolState;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface TransitionModel {
  List<BaseSwarmMessage> getMessagesToSend();
  SwarmProtocolState getNextSwarmProtocolState();
}
