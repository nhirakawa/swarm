package com.github.nhirakawa.swarm;

import javax.inject.Singleton;

import com.github.nhirakawa.swarm.dagger.SwarmDaggerModule;
import com.github.nhirakawa.swarm.transport.server.SwarmServer;

import dagger.Component;

@Singleton
@Component(modules = SwarmDaggerModule.class)
public interface SwarmComponent {

  SwarmServer buildServer();

}
