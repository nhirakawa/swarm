# swarm

This directory holds the Rust implementation of `swarm`, as well as an inter-process communication router. Only the router is currently implemented.

## Commands

### `router`

The `router` command is responsible for spawning members of a `swarm` cluster and routing messages between them. When enabled, an admin UI is hosted by an HTTP API; each node's state is proxied through the router. The router can dynamically create new nodes and force nodes to shutdown. Property-based testing may be implemented across heterogeneous implementations.
