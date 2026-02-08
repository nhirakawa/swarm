package com.github.nhirakawa.swarm.runner.admin;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface AdminConfigModel {
	int getPort();
}
