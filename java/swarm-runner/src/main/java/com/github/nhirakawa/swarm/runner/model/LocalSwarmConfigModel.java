package com.github.nhirakawa.swarm.runner.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface LocalSwarmConfigModel extends NodeConfigModel {
	int getNumberOfNodes();
}
