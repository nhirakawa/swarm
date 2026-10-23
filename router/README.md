# swarm-router

This directory holds `swarm-router`, an inter-process communication router that spawns members of a `swarm` cluster (in any implementation language) and routes messages between them.

## Overview

The router is responsible for spawning members of a `swarm` cluster and routing messages between them. When enabled, an admin UI is hosted by an HTTP API; each node's state is proxied through the router. The router can dynamically create new nodes and force nodes to shutdown. Property-based testing may be implemented across heterogeneous implementations.

## Implementations

Implementations that are supported by the router are expected to conform to a standard interface. Implementations are specified in a JSON configuration file with two top-level fields: `implementations` and `nodes`.

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

### CLI Arguments

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

### HTTP API

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

## Line Protocol

The router and each node exchange protocol messages over the node's standard streams:

| Stream | Direction | Contents |
|---|---|---|
| stdin | router → node | Messages addressed to this node |
| stdout | node → router | Messages this node is sending |
| stderr | node → (inherited) | Logs and diagnostics; never parsed by the router |

Nodes MUST NOT write anything other than protocol messages to stdout. Send all logging to stderr.

### Envelope

Each message is a single line of UTF-8 JSON terminated by `\n`:

```json
{"source": "node-1", "target": "node-2", "packet": "<base64>"}
{"source": "node-1", "target": "__all__", "packet": "<base64>"}
{"source": "node-1", "target": "__router__", "control": {"kind": "ready"}}
```

> **Note:** This section describes the target format. The router and the Java node still use a single `payload` field and `MULTICAST` discovery until the migration lands.

| Field | Type | Description |
|---|---|---|
| `source` | string | Address of the sender, or `__router__` |
| `target` | string | Address of the recipient, `__router__`, or `__all__` |
| `packet` | string | Standard (RFC 4648, padded) base64 encoding of a framed protocol message. Sent node → node |
| `control` | object | A control message tagged by `kind`. Sent between a node and the router |

Every line has exactly one of `packet` or `control`, and receivers branch on which field is present, never on `source` or `target`. Node IDs starting with `__` are reserved for router addresses. `__router__` is the router's own address: the `target` of every node → router `control` line and the `source` of every router → node one. The line format has no version field, because the router and its nodes always ship together.

The router forwards `packet` lines unchanged to the target's stdin and never decodes them. `__all__` is the broadcast address: the router copies a `packet` sent to `__all__` to every Ready node except the sender, and simulated partitions apply to each copy separately. It drops and logs:

- lines that are not valid JSON, or that have both body fields or neither;
- a `control` addressed to a node or to `__all__`, or a `packet` addressed to `__router__`;
- messages whose `target` is not a registered node.

A write to a node's stdin that does not finish within 5 seconds is abandoned.

### Control Messages

| `kind` | Direction | Other fields in `control` | Meaning |
|---|---|---|---|
| `ready` | node → router | none | Node has initialized and can accept protocol messages. Sent once |
| `status_request` | router → node | `id`: integer | Ask for a state snapshot |
| `status` | node → router | `id` (echoed), `snapshot`: object | Reply to `status_request`; `snapshot` uses the `GET /status` schema above |

Receivers ignore unknown `kind` values.

Control messages cover only the process and observation. Everything nodes say to each other, discovery included, travels as packets: a node broadcasts `DISCOVERY_REQUEST` to `__all__` repeatedly while joining, then periodically at a lower rate so it can find peers again after a partition heals, and merges the `DISCOVERY_RESPONSE` replies.

### Packet Frame

The decoded `packet` is a fixed 22-byte header followed by the message body. All multi-byte integers are big-endian.

| Offset | Size | Field | Description |
|---|---|---|---|
| 0 | 1 | `version` | Message format version. Must be `0` |
| 1 | 1 | `type` | `0` = `PING_ACK`, `1` = `PING_REQUEST`, `2` = `DISCOVERY_REQUEST`, `3` = `DISCOVERY_RESPONSE` |
| 2 | 1 | `compression` | `0` = none, `1` = gzip. Nodes currently send `0` |
| 3 | 1 | `serialization` | `0` = JSON, `1` = CBOR. Nodes currently send `0` |
| 4 | 2 | `payloadLength` | Unsigned length of the body in bytes. Must equal the decoded length minus 22, and be less than 65535 |
| 6 | 4 | `messageId` | Unsigned per-sender counter, starting at 1 |
| 10 | 8 | `timestamp` | Send time in Unix epoch milliseconds |
| 18 | 4 | `checksum` | Reserved for CRC32. Currently always `0` and not verified |

Receivers reject a frame that is shorter than 22 bytes, or whose `payloadLength` does not match the body length.

### Message Bodies

Bodies are JSON objects. The message type is carried only in the header, not in the body. Addresses are node IDs encoded as JSON strings.

**`PING_REQUEST`**

| Field | Type | Description |
|---|---|---|
| `source` | string | Sender |
| `target` | string | Recipient |
| `proxyFor` | string \| null | For an indirect ping, the node on whose behalf this ping is sent |
| `protocolPeriodId` | integer | Sender's current protocol period |
| `gossip` | array of member status | Piggybacked membership updates |

**`PING_ACK`**

| Field | Type | Description |
|---|---|---|
| `source` | string | Sender |
| `target` | string | Recipient |
| `proxyFor` | string \| null | Echoes `proxyFor` from the request being acknowledged |
| `protocolPeriodId` | integer | Protocol period of the request being acknowledged |
| `incarnation` | integer | Sender's incarnation number |
| `gossip` | array of member status | Piggybacked membership updates |

**`DISCOVERY_REQUEST`**

| Field | Type | Description |
|---|---|---|
| `source` | string | Sender |
| `target` | string | Recipient |

**`DISCOVERY_RESPONSE`**

| Field | Type | Description |
|---|---|---|
| `source` | string | Sender |
| `target` | string | Recipient |
| `memberList` | array of member status | Sender's view of cluster membership |

A **member status** has the same shape as `memberStatuses[]` in `GET /status`:

```json
{"address": "node-3", "incarnation": 1, "type": "ALIVE"}
```

where `type` is `ALIVE`, `SUSPECTED`, or `CONFIRMED`.

### Example

A `DISCOVERY_REQUEST` from `node-1` to `node-2`:

```
body   = {"source":"node-1","target":"node-2"}                          (37 bytes)
header = 00 02 00 00 | 00 25 | 00 00 00 01 | 00 00 01 9a 0c a6 0c 00 | 00 00 00 00
         ver type cmp ser  len    messageId     timestamp (ms)          checksum
```

On the wire:

```json
{"source":"node-1","target":"node-2","packet":"AAIAAAAlAAAAAQAAAZoMpgwAAAAAAHsic291cmNlIjoibm9kZS0xIiwidGFyZ2V0Ijoibm9kZS0yIn0="}
```
