package com.github.nhirakawa.swarm.dagger;

import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import dagger.Module;
import dagger.Provides;

@Module(includes = SwarmProtocolModule.class)
public class SwarmNettyRunnerModule {

  @Provides
  static SwarmMessageSender provideSwarmMessageSender(SwarmClient swarmClient) {
    return swarmClient;
  }
}
