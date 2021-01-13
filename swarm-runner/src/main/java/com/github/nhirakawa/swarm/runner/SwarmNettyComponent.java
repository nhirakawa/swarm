package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.dagger.SwarmNettyRunnerModule;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = SwarmNettyRunnerModule.class)
public interface SwarmNettyComponent {
  SwarmService buildService();
}
