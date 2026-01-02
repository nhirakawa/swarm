package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.runner.cli.SwarmNodeConverter;
import picocli.CommandLine;

@CommandLine.Command
public class SwarmLauncher {

  public static void main(String[] args) {
    CommandLine commandLine = new CommandLine(new SwarmLauncher());
    commandLine.registerConverter(SwarmNode.class, new SwarmNodeConverter());
    System.exit(commandLine.execute(args));
  }
}
