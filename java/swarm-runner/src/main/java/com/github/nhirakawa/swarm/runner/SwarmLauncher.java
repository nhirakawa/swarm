package com.github.nhirakawa.swarm.runner;

import com.github.nhirakawa.swarm.runner.cmd.Local;
import com.github.nhirakawa.swarm.runner.guice.GuiceFactory;
import com.github.nhirakawa.swarm.runner.guice.LocalSwarmModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import picocli.CommandLine;

@CommandLine.Command(subcommands = Local.class)
public class SwarmLauncher {

	SwarmLauncher() {}

	static void main(String[] args) {
		//noinspection InstantiationOfUtilityClass
		CommandLine commandLine = new CommandLine(
			new SwarmLauncher(),
			new GuiceFactory(buildInjector())
		);
		System.exit(commandLine.execute(args));
	}

	private static Injector buildInjector() {
		return Guice.createInjector(new LocalSwarmModule());
	}
}
