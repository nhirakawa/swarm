package com.github.nhirakawa.swarm.protocol.util;

import com.github.nhirakawa.swarm.protocol.model.SwarmState;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public class SwarmStateBuffer {
  private final Deque<SwarmState> delegate;
  private final int bufferSize;

  public SwarmStateBuffer(SwarmState initialState, int bufferSize) {
    this.delegate =
      new ConcurrentLinkedDeque<>(Collections.singletonList(initialState));
    this.bufferSize = bufferSize;
  }

  public void add(SwarmState swarmState) {
    delegate.addFirst(swarmState);

    if (delegate.size() > bufferSize) {
      delegate.removeLast();
    }
  }

  public SwarmState getCurrent() {
    return delegate.getFirst();
  }

  public Collection<SwarmState> getAll() {
    return Collections.unmodifiableCollection(delegate);
  }
}
