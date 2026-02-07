package com.github.nhirakawa.swarm.protocol.transport.mem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.nhirakawa.swarm.protocol.model.SwarmAddress;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryTransportRegistryTest {

  private InMemoryTransportRegistry registry;
  private SwarmAddress address1;
  private SwarmAddress address2;
  private InMemoryTransport transport1;
  private InMemoryTransport transport2;

  @BeforeEach
  void setUp() {
    NetworkSimulationConfig config = DefaultNetworkSimulationConfig.perfect();
    registry = new InMemoryTransportRegistry();
    address1 = new SwarmAddress("192.168.1.1", 8080, "node-1");
    address2 = new SwarmAddress("192.168.1.2", 8080, "node-2");
    // Create transports with a dummy registry and simulator to avoid auto-registration
    InMemoryTransportRegistry dummyRegistry = new InMemoryTransportRegistry();
    NetworkSimulator dummySimulator = new NetworkSimulator(dummyRegistry, config);
    transport1 = new InMemoryTransport(address1, dummyRegistry, dummySimulator);
    transport2 = new InMemoryTransport(address2, dummyRegistry, dummySimulator);
  }

  @Test
  void testRegisterAndLookup() {
    registry.register(address1, transport1);

    Optional<InMemoryTransport> result = registry.lookup(address1);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(transport1);
  }

  @Test
  void testLookupNonExistent() {
    Optional<InMemoryTransport> result = registry.lookup(address1);

    assertThat(result).isEmpty();
  }

  @Test
  void testRegisterDuplicateThrowsException() {
    registry.register(address1, transport1);

    assertThatThrownBy(() -> registry.register(address1, transport2))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("already registered");
  }

  @Test
  void testDeregister() {
    registry.register(address1, transport1);
    registry.deregister(address1);

    Optional<InMemoryTransport> result = registry.lookup(address1);

    assertThat(result).isEmpty();
  }

  @Test
  void testDeregisterNonExistent() {
    // Should not throw
    registry.deregister(address1);

    assertThat(registry.size()).isEqualTo(0);
  }

  @Test
  void testMultipleRegistrations() {
    registry.register(address1, transport1);
    registry.register(address2, transport2);

    assertThat(registry.size()).isEqualTo(2);
    assertThat(registry.lookup(address1)).isPresent();
    assertThat(registry.lookup(address2)).isPresent();
  }

  @Test
  void testClear() {
    registry.register(address1, transport1);
    registry.register(address2, transport2);

    registry.clear();

    assertThat(registry.size()).isEqualTo(0);
    assertThat(registry.lookup(address1)).isEmpty();
    assertThat(registry.lookup(address2)).isEmpty();
  }
}
