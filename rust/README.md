# swarm

This directory holds the Rust implementation of `swarm`, as well as an inter-process communication router. Only the router is currently implemented.

## Commands

### `router`

The `router` command is responsible for spawning members of a `swarm` cluster and routing messages between them. When enabled, an admin UI is hosted by an HTTP API; each node's state is proxied through the router. The router can dynamically create new nodes and force nodes to shutdown. Property-based testing may be implemented across heterogeneous implementations.

#### Implementations

Implementations that are supported by `router` are expected to conform to a standard interface. Implementations are specified in a JSON configuration file with two top-level fields: `implementations` and `nodes`.

**`implementations`** is a map from an arbitrary name to an implementation descriptor:

| Field | Type | Required | Description |
|---|---|---|---|
| `executable` | string (path) | yes | Path to the executable (or script) used to launch a node |
| `args` | object (string → string) | no | Extra CLI arguments passed to every node of this implementation, as `--key value` pairs |

**`nodes`** is an array of node descriptors:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique node ID within the cluster; passed as `--address` |
| `implementation` | string | yes | Key into `implementations` that identifies which executable to use |
| `admin_port` | integer | yes | Port on which the node's HTTP admin API will listen; passed as `--admin-port` |
| `args` | object (string → string) | no | Extra CLI arguments for this specific node; merged with (and override) the implementation-level `args` |

Example:

```json
{
  "implementations": {
    "java": {
      "executable": "../java/run-local-node.sh",
      "args": {
        "protocol-period": "PT10S",
        "message-timeout": "PT1S"
      }
    }
  },
  "nodes": [
    { "id": "node-1", "implementation": "java", "admin_port": 8081 },
    { "id": "node-2", "implementation": "java", "admin_port": 8082 },
    { "id": "node-3", "implementation": "java", "admin_port": 8083 }
  ]
}
```

##### CLI Arguments

The router itself accepts:

| Argument | Description |
|---|---|
| `--config <path>` | Path to the JSON configuration file (required) |

Each supported implementation MUST support the following CLI arguments, which the router passes automatically when spawning nodes:

| Argument | Description |
|---|---|
| `--address <id>` | The node's string ID, unique within the cluster |
| `--admin-port <port>` | Port on which the node hosts its HTTP admin API |
| `--peer <id>` | A peer node ID; passed once per peer (may appear multiple times) |

Additional arguments may be specified in the configuration file under `implementations[name].args` (applied to every node using that implementation) or `nodes[i].args` (applied to a specific node). Node-level args override implementation-level args with the same key.

##### HTTP API

Each supported implementation MUST expose the following HTTP endpoints on the port passed as `--admin-port`.

---

**`GET /health`**

Liveness check. Returns `200` once the node has initialized, or `503` while it is still starting up.

| Status | Body |
|---|---|
| `200 OK` | `{"status": "ok"}` |
| `503 Service Unavailable` | `{"status": "starting"}` |

The router polls this endpoint every 5 seconds and uses it to track which nodes are active.

---

**`GET /status`**

Returns a JSON snapshot of the node's current protocol state. Returns `503` while the node is still initializing (no snapshot available yet).

| Status | Body |
|---|---|
| `200 OK` | See schema below |
| `503 Service Unavailable` | *(empty body)* |

Response schema:

```json
{
  "localAddress": "<node-id>",
  "protocolPeriodId": 42,
  "incarnation": 3,
  "memberStatuses": [
    {
      "address": "<peer-node-id>",
      "type": "ALIVE | SUSPECTED | CONFIRMED",
      "incarnation": 1
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `localAddress` | string | This node's address/ID |
| `protocolPeriodId` | integer | Monotonically increasing counter of completed protocol periods |
| `incarnation` | integer | This node's incarnation number |
| `memberStatuses` | array | One entry per known peer |
| `memberStatuses[].address` | string | Peer node's address/ID |
| `memberStatuses[].type` | string | SWIM membership status: `ALIVE`, `SUSPECTED`, or `CONFIRMED` (dead) |
| `memberStatuses[].incarnation` | integer | Peer's incarnation number as last observed |
