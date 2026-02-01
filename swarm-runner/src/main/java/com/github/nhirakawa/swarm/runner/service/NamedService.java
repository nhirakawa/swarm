package com.github.nhirakawa.swarm.runner.service;

import com.google.common.util.concurrent.Service;

public interface NamedService extends Service {
  String getName();
}
