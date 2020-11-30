package com.github.nhirakawa.swarm;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class BannerUtil {

  private BannerUtil() {
    throw new UnsupportedOperationException();
  }

  static String getOrDefault(String defaultBanner) {
    try {
      return Resources.toString(
        Resources.getResource("banner.txt"),
        StandardCharsets.UTF_8
      );
    } catch (IOException e) {
      return defaultBanner;
    }
  }
}
