package com.github.nhirakawa.swarm.runner.util;

import com.google.common.util.concurrent.Service;

public interface NamedService extends Service {
  String getName();
}
