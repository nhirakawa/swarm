package com.github.nhirakawa.swarm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;

public class Runner {

  public static void main(String... args) {
    System.out.println(getBanner());
  }

  private static String getBanner() {
    try {
      return Resources.toString(Resources.getResource("banner.txt"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "swarm";
    }
  }
}
