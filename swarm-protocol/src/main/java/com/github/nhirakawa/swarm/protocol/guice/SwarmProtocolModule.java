package com.github.nhirakawa.swarm.protocol.guice;

import com.github.nhirakawa.swarm.protocol.util.InjectableRandom;
import com.github.nhirakawa.swarm.protocol.util.InjectableThreadLocalRandom;
import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import java.time.Clock;
import java.time.InstantSource;

public class SwarmProtocolModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(InstantSource.class).to(Clock.class);
    bind(InjectableRandom.class).to(InjectableThreadLocalRandom.class);
    bind(EventBus.class).toInstance(new EventBus("swarm-event-bus"));
  }
}
