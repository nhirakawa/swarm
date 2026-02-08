package com.github.nhirakawa.swarm.runner.admin.http;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import com.github.nhirakawa.swarm.protocol.state.MemberStatus;
import com.github.nhirakawa.swarm.protocol.state.StateSnapshot;
import com.google.common.base.Suppliers;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ContainerHandler implements Handler {

	private static final Supplier<String> TEMPLATE = Suppliers.memoize(
			() -> {
				try {
					return Resources.toString(Resources.getResource("admin/template/container.template.html"), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
	);

	private final Jinjava jinjava;
	private final Supplier<List<StateSnapshot>> snapshotListSupplier;

	public ContainerHandler(Jinjava jinjava, Supplier<List<StateSnapshot>> snapshotListSupplier) {
		this.jinjava = jinjava;
		this.snapshotListSupplier = snapshotListSupplier;
	}

	@Override
	public void handle(@Nonnull Context context) throws Exception {
		List<Map<String, Object>> snapshots = snapshotListSupplier.get().stream()
				.map(ContainerHandler::toTemplateMap)
				.toList();
		context.html(jinjava.render(TEMPLATE.get(), Map.of("snapshots", snapshots)));
	}

	private static Map<String, Object> toTemplateMap(StateSnapshot snapshot) {
		List<Map<String, Object>> memberStatuses = snapshot.getMemberStatuses().stream()
				.map(ContainerHandler::toTemplateMap)
				.toList();

		return Map.of(
				"localAddress", toTemplateMap(snapshot.getLocalAddress()),
				"protocolPeriodId", snapshot.getProtocolPeriodId(),
				"incarnation", snapshot.getIncarnation(),
				"memberStatuses", memberStatuses
		);
	}

	private static Map<String, Object> toTemplateMap(MemberStatus memberStatus) {
		return Map.of(
				"address", toTemplateMap(memberStatus.address()),
				"type", memberStatus.type(),
				"incarnation", memberStatus.incarnation()
		);
	}

	private static Map<String, Object> toTemplateMap(SwarmAddress address) {
		return Map.of(
				"address", address.address(),
				"port", address.port()
		);
	}
}
