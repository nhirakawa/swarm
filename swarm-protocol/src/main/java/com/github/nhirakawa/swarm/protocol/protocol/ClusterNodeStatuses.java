package com.github.nhirakawa.swarm.protocol.protocol;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.MemberStatusUpdate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ClusterNodeStatuses {
  private static final Logger LOG = LoggerFactory.getLogger(
    ClusterNodeStatuses.class
  );

  private final ConcurrentMap<SwarmNode, MemberStatusUpdate> recentUpdateByNode = new ConcurrentHashMap<>();

  @Inject
  ClusterNodeStatuses() {}

  public void apply(MemberStatusUpdate memberStatusUpdate) {
    recentUpdateByNode.merge(
      memberStatusUpdate.getSwarmNode(),
      memberStatusUpdate,
      ClusterNodeStatuses::pickOne
    );
  }

  private static MemberStatusUpdate pickOne(
    MemberStatusUpdate oldValue,
    MemberStatusUpdate newValue
  ) {
    // FAILED overrides everything
    if (newValue.getNewMemberStatus() == MemberStatus.FAILED) {
      return newValue;
    }

    if (oldValue.getNewMemberStatus() == MemberStatus.FAILED) {
      return oldValue;
    }

    // if the new incarnation number isn't at least == old incarnation, old value wins
    if (newValue.getIncarnationNumber() < oldValue.getIncarnationNumber()) {
      return oldValue;
    }

    if (
      newValue.getNewMemberStatus() == MemberStatus.ALIVE &&
      oldValue.getNewMemberStatus() == MemberStatus.SUSPECTED
    ) {
      if (newValue.getIncarnationNumber() > oldValue.getIncarnationNumber()) {
        return newValue;
      } else {
        return oldValue;
      }
    }

    if (
      newValue.getNewMemberStatus() == MemberStatus.ALIVE &&
      oldValue.getNewMemberStatus() == MemberStatus.ALIVE
    ) {
      if (newValue.getIncarnationNumber() > oldValue.getIncarnationNumber()) {
        return newValue;
      } else {
        return oldValue;
      }
    }

    if (
      newValue.getNewMemberStatus() == MemberStatus.SUSPECTED &&
      oldValue.getNewMemberStatus() == MemberStatus.SUSPECTED
    ) {
      if (newValue.getIncarnationNumber() > oldValue.getIncarnationNumber()) {
        return newValue;
      } else {
        return oldValue;
      }
    }

    if (
      newValue.getNewMemberStatus() == MemberStatus.SUSPECTED &&
      oldValue.getNewMemberStatus() == MemberStatus.ALIVE
    ) {
      if (newValue.getIncarnationNumber() >= oldValue.getIncarnationNumber()) {
        return newValue;
      } else {
        return oldValue;
      }
    }

    LOG.warn(
      "Found unhandled state - defaulting to old rule. Old value {}, new value {}",
      oldValue,
      newValue
    );
    return oldValue;
  }

  Optional<MemberStatusUpdate> get(SwarmNode swarmNode) {
    return Optional.ofNullable(recentUpdateByNode.get(swarmNode));
  }
}
