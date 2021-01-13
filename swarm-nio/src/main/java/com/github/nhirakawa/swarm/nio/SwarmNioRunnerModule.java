package com.github.nhirakawa.swarm.nio;

import com.github.nhirakawa.swarm.protocol.dagger.SwarmProtocolModule;
import com.github.nhirakawa.swarm.protocol.protocol.SwarmMessageSender;
import com.google.common.util.concurrent.Service;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

@Module(includes = SwarmProtocolModule.class)
public class SwarmNioRunnerModule {

  @Provides
  static SwarmMessageSender provideSwarmMessageSender(
    SwarmNioServer swarmNioServer
  ) {
    return swarmNioServer;
  }

  @Provides
  @Named("swarm-server")
  static Service provideSwarmServer(SwarmNioServer swarmNioServer) {
    return swarmNioServer;
  }
}
