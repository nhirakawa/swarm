package com.github.nhirakawa.swarm.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;

final class BannerUtil {

  private BannerUtil() {
    throw new UnsupportedOperationException();
  }

  static String getOrDefault(String resourceName, String defaultBanner) {
    try {
      return Resources.toString(
        Resources.getResource(resourceName),
        StandardCharsets.UTF_8
      );
    } catch (IOException e) {
      return defaultBanner;
    }
  }
}
