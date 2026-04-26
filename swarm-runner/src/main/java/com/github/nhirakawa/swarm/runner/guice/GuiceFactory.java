package com.github.nhirakawa.swarm.runner.guice;

import com.google.inject.Injector;
import picocli.CommandLine;

public class GuiceFactory implements CommandLine.IFactory {

	private final Injector injector;

	public GuiceFactory(Injector injector) {
		this.injector = injector;
	}

	@Override
	public <K> K create(Class<K> cls) throws Exception {
		return injector.getInstance(cls);
	}
}
