package com.github.nhirakawa.swarm.protocol.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.github.nhirakawa.swarm.protocol.config.SwarmNode;
import com.github.nhirakawa.swarm.protocol.model.MemberStatusUpdate;

public class ClusterNodeStatusesTest {

	private static final SwarmNode SWARM_NODE = SwarmNode.builder()
			.setHost("host")
			.setPort(1)
			.build();

	private ClusterNodeStatuses clusterNodeStatuses;

	@Before
	public void setup() {
		clusterNodeStatuses = new ClusterNodeStatuses();
	}

	@Test
	public void itOverridesSuspectedWithAliveIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itDoesNotOverrideSuspectedWithAliveIfLesserOrEqualIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(initial);
	}


	@Test
	public void itOverridesAliveWithAliveIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itDoesNotOverrideAliveWithAliveIfLesserIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(initial);
	}

	@Test
	public void itOverridesSuspectWithSuspectIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itDoesNotOverrideSuspectWithSuspectIfLesserIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(initial);
	}

	@Test
	public void itOverridesAliveWithSuspectedIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesAliveWithSuspectedIfEqualIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itDoesNotOverrideSuspectWithAliveIfLesserIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(initial);
	}

	@Test
	public void itOverridesAliveWithFailedIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesAliveWithFailedIfLesserIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesAliveWithFailedIfEqualIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.ALIVE)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesSuspectedWithFailedIfGreaterIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesSuspectedWithFailedIfLesserIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(2)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

	@Test
	public void itOverridesSuspectedWithFailedIfEqualIncarnationNumber() {
		MemberStatusUpdate initial = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.SUSPECTED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(initial);

		MemberStatusUpdate update = MemberStatusUpdate.builder()
				.setSwarmNode(SWARM_NODE)
				.setNewMemberStatus(MemberStatus.FAILED)
				.setIncarnationNumber(1)
				.build();

		clusterNodeStatuses.apply(update);

		assertThat(clusterNodeStatuses.get(SWARM_NODE)).contains(update);
	}

}
