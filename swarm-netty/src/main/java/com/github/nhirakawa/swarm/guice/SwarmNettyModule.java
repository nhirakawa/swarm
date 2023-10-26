package com.github.nhirakawa.swarm.guice;

import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.github.nhirakawa.swarm.transport.server.SwarmNettyServer;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class SwarmNettyModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(SwarmMessageSender.class).to(SwarmClient.class);
    bind(Key.get(Service.class, Names.named("swarm-server")))
      .to(SwarmNettyServer.class);
  }
}
