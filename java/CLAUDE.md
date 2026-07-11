# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java implementation of the [SWIM protocol](http://www.cs.cornell.edu/projects/quicksilver/public_pdfs/SWIM.pdf) — a gossip-based membership and failure detection protocol. All nodes run in-process using an in-memory transport layer (no real networking).

## Build & test commands

```bash
# Build everything
mvn clean install

# Run tests only
mvn test

# Run a single test class
mvn test -pl swarm-protocol -Dtest=MemberRegistryTest

# Run a single test method
mvn test -pl swarm-protocol -Dtest=MemberRegistryTest#testMethodName

# Format check (Spotless + prettier-plugin-java)
mvn spotless:check

# Apply formatting
mvn spotless:apply

# Run the local simulation
mvn -pl swarm-runner exec:java -Dexec.mainClass=com.github.nhirakawa.swarm.runner.SwarmLauncher -Dexec.args="local --config classpath:config/local.json"
```

## Code architecture

Two Maven modules:

**`swarm-protocol`** — protocol logic, transport abstractions, and in-memory simulation:
- `SwarmService` — Guava `AbstractIdleService` that owns a `SwarmStateMachine` + `SwarmTransport`. Start/stop both together.
- `SwarmStateMachine` — drives the SWIM protocol loop. On each tick it polls `SwarmMessageReceiver` and calls `applyTick()` / `applyPingAck()` / `applyPingRequest()` / `applyDiscovery*()` on the current `SwarmProtocolState`.
- `SwarmProtocolState` — sealed class hierarchy representing the four states a node can be in:
  - `InitializingProtocolState` — broadcasts discovery requests until at least one other member responds.
  - `WaitingForNextProtocolPeriodProtocolState` — idle between protocol periods.
  - `WaitingForAckProtocolState` — waiting for a direct ping-ack.
  - `WaitingForPingProxyProtocolState` — waiting for an indirect ping-ack via a proxy.
- `MemberStatus` — sealed interface with three records: `Alive`, `Suspected`, `Confirmed`. Merge semantics follow SWIM incarnation rules.
- `MemberRegistry` — holds each peer's `MemberStatus` and provides gossip payloads and failure sub-group selection.
- `transport/mem/` — entirely in-memory transport: `NetworkSimulator` routes serialized `WireMessage`s between `InMemoryTransport` instances with optional latency simulation (`UniformLatencyDistribution`, `GaussianLatencyDistribution`).

**`swarm-runner`** — wiring, CLI, and admin UI:
- `SwarmLauncher` — picocli entry point; `local` subcommand spins up N in-process nodes.
- `Local` command — reads `LocalSwarmConfig` (JSON), creates `InMemorySwarmAddress` instances, builds `SwarmService`s via `SwarmServiceFactory`, and starts an optional `AdminService`.
- `AdminService` — Javalin HTTP server (default port 8080) with three routes: `GET /app/container` (Jinja2 dashboard), `POST /app/nodes` (add a node), `DELETE /app/nodes/{address}` (shut down a node).
- Guice (`LocalSwarmModule`) wires `NetworkSimulator`, `SwarmServiceFactory`, `Jinjava`, and `ObjectMapper` together.
- Config is JSON; durations use ISO-8601 format (e.g. `"PT10S"`).

## Key patterns

- **Immutables**: all config/model types are `@Value.Immutable` with `@ImmutableStyle`. The generated class drops the `Model` suffix (e.g. `SwarmConfigModel` → `SwarmConfig`). Always use the generated builder.
- **Transitions**: `SwarmProtocolState` methods return `Optional<Transition>`. A `Transition` carries the next state and zero or more `StateMachineMessage`s to send. The state machine sends them after updating `stateSnapshot`.
- **Error Prone**: enforced at compile time. Unused imports, unused variables, missing braces, missing `@Override`, etc. are all compile errors.
- **Formatting**: Spotless with `prettier-plugin-java` using tabs. Run `mvn spotless:apply` before committing.
