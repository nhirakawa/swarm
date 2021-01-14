package com.github.nhirakawa.swarm.dagger;

import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.github.nhirakawa.swarm.transport.client.SwarmClient;
import com.github.nhirakawa.swarm.transport.server.SwarmNettyServer;
import com.google.common.util.concurrent.Service;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

@Module(includes = SwarmProtocolModule.class)
public class SwarmNettyRunnerModule {

  @Provides
  static SwarmMessageSender provideSwarmMessageSender(SwarmClient swarmClient) {
    return swarmClient;
  }

  @Provides
  @Named("swarm-server")
  static Service provideSwarmServer(SwarmNettyServer swarmNettyServer) {
    return swarmNettyServer;
  }
}
