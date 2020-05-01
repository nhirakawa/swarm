package com.github.nhirakawa.swarm;

import com.github.nhirakawa.swarm.dagger.SwarmNettyRunnerModule;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = SwarmNettyRunnerModule.class)
public interface SwarmComponent {
  SwarmServer buildServer();
}
