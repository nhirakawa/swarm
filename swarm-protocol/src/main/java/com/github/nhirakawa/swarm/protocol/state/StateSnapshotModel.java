package com.github.nhirakawa.swarm.protocol.state;

import com.github.nhirakawa.immutable.style.guava.ImmutableStyle;
import com.github.nhirakawa.swarm.protocol.model.address.SwarmAddress;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface StateSnapshotModel {
	SwarmAddress getLocalAddress();
	long getProtocolPeriodId();
	long getIncarnation();
	List<MemberStatus> getMemberStatuses();
}
