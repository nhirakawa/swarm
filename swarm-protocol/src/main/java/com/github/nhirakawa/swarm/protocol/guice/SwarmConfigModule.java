package com.github.nhirakawa.swarm.protocol.guice;

import com.github.nhirakawa.swarm.protocol.config.SwarmConfig;
import com.google.inject.AbstractModule;

public class SwarmConfigModule extends AbstractModule {

  private final SwarmConfig swarmConfig;

  public SwarmConfigModule(SwarmConfig swarmConfig) {
    this.swarmConfig = swarmConfig;
  }

  @Override
  protected void configure() {
    bind(SwarmConfig.class).toInstance(swarmConfig);
  }
}
