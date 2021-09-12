package com.github.nhirakawa.swarm.protocol.util;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEventBus extends EventBus {
  private final List<Object> objects = Collections.synchronizedList(
    new ArrayList<>()
  );

  @Override
  public void register(Object object) {}

  @Override
  public void unregister(Object object) {}

  @Override
  public void post(Object event) {
    objects.add(event);
  }
}
