package com.github.nhirakawa.swarm.protocol.model;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.google.common.base.Preconditions;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface ProxyTargetsModel {
  List<ProxyTargetModel> getProxyTargets();

  @Value.Check
  default void check() {
    Preconditions.checkArgument(
      !getProxyTargets().isEmpty(),
      "Must provide at least one proxy target"
    );
  }
}
