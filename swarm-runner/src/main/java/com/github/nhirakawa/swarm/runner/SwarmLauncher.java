package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.runner.cmd.Local;
import picocli.CommandLine;

@CommandLine.Command (
    subcommands = Local.class
)
public class SwarmLauncher {

  SwarmLauncher() {}

  static void main(String[] args) {
		//noinspection InstantiationOfUtilityClass
		CommandLine commandLine = new CommandLine(new SwarmLauncher());
    System.exit(commandLine.execute(args));
  }
}
