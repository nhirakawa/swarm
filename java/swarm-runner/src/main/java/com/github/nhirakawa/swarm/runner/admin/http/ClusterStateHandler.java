package com.github.nhirakawa.swarm.runner.admin.http;

import com.github.nhirakawa.swarm.protocol.state.MemberStatus;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

public class ClusterStateHandler implements Handler {

	private final Supplier<StateSnapshot> snapshotSupplier;

	public ClusterStateHandler(Supplier<StateSnapshot> snapshotSupplier) {
		this.snapshotSupplier = snapshotSupplier;
	}

	@Override
	public void handle(@Nonnull Context context) throws Exception {
		StateSnapshot snapshot = snapshotSupplier.get();
		if (snapshot == null) {
			context.status(503);
			return;
		}
		context.json(toResponseMap(snapshot));
	}

	private static Map<String, Object> toResponseMap(StateSnapshot snapshot) {
		List<Map<String, Object>> memberStatuses = snapshot
			.getMemberStatuses()
			.stream()
			.map(ClusterStateHandler::toMemberStatusMap)
			.toList();

		return Map.of(
			"localAddress",
			snapshot.getLocalAddress().asString(),
			"protocolPeriodId",
			snapshot.getProtocolPeriodId(),
			"incarnation",
			snapshot.getIncarnation(),
			"memberStatuses",
			memberStatuses
		);
	}

	private static Map<String, Object> toMemberStatusMap(
		MemberStatus memberStatus
	) {
		return Map.of(
			"address",
			memberStatus.address().asString(),
			"type",
			memberStatus.type(),
			"incarnation",
			memberStatus.incarnation()
		);
	}
}
