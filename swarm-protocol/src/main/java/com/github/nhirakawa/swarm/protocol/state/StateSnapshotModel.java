package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@ImmutableStyle
public interface StateSnapshotModel {
	SwarmAddress getLocalAddress();
	long getProtocolPeriodId();
	long getIncarnation();
	List<MemberStatus> getMemberStatuses();
}
