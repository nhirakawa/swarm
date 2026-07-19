# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
cargo build          # compile
cargo run -- router --config <path>   # run the router
cargo test           # run all tests
cargo clippy         # lint
```

## What this is

A Rust implementation of the **SWIM protocol** — a router/coordinator that spawns node processes and routes messages between them over stdin/stdout using newline-delimited JSON.

## Architecture

The entry point (`main.rs`) parses a JSON config, spawns one child process per node, and connects them through a shared registry.

**IPC contract:** each child process reads JSON messages from its stdin and writes JSON messages to its stdout. The router reads stdout lines, deserializes them as `Message`, and forwards each to the target node's stdin. One `\n`-terminated JSON object per line.

**Registry** (`registry.rs`) is an actor: a background tokio task owns a `HashMap<NodeId, ChildStdin>` and accepts commands through an `mpsc` channel. The public handle (`RegistryHandle`) is cheaply cloneable and sends typed `RegistryRequest` variants (`Register`, `Deregister`, `Route`) with `oneshot` response channels.

**Config** (`config.rs`): JSON file with two fields:
- `implementations`: map of name → executable path
- `nodes`: list of `{ id, implementation }` where `implementation` references a key in the map

**Message** (`message.rs`): `{ source, target, payload }` — all strings, serialized with serde_json.

## Key design notes

- The registry actor serializes all node state access; no locks needed.
- Nodes are identified only by string ID; the router does not know about SWIM protocol internals.
- Child process lifecycle (restarting crashed nodes, timeouts on stdin writes) is not yet implemented — noted via `<review>` comments in the code.
