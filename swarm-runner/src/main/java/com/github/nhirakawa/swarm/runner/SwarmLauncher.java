package com.github.nhirakawa.swarm.runner;

import picocli.CommandLine;

@CommandLine.Command(
  subcommands = { SwarmLocalClusterRunner.class, SwarmNettyRunner.class }
)
public class SwarmLauncher {

  public static void main(String[] args) {
    CommandLine commandLine = new CommandLine(new SwarmLauncher());
    System.exit(commandLine.execute(args));
  }
}
