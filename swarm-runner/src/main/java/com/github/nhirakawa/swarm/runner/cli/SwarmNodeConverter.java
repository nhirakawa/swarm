package com.github.nhirakawa.swarm.runner.cli;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.util.List;
import picocli.CommandLine;

public class SwarmNodeConverter
  implements CommandLine.ITypeConverter<SwarmNode> {

  private static final Splitter ON_COLON = Splitter.on(":");

  @Override
  public SwarmNode convert(String value) throws Exception {
    List<String> parts = ON_COLON.splitToList(value);
    Preconditions.checkArgument(
      parts.size() == 2,
      "Expected %s to have size 2",
      parts
    );

    String host = parts.get(0);
    int port = Integer.parseInt(parts.get(1));

    return SwarmNode.builder().setHost(host).setPort(port).build();
  }
}
