package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.nio.SwarmNioRunnerModule;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = SwarmNioRunnerModule.class)
public interface SwarmNioComponent {
  SwarmService buildService();
}
