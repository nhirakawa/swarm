package com.github.nhirakawa.swarm.protocol.util;

import com.github.nhirakawa.swarm.protocol.protocol.SwarmFailureInjector;
import com.google.common.eventbus.EventBus;
import java.time.Clock;

public final class Fakes {

  private Fakes() {
    throw new UnsupportedOperationException();
  }

  public static Clock clock() {
    return new FakeClock();
  }

  public static EventBus eventBus() {
    return new FakeEventBus();
  }
}
