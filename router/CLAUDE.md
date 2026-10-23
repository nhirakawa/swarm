# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
cargo build          # compile
cargo run -- --config <path>   # run the router
cargo test           # run all tests
cargo clippy         # lint
```

## What this is

A language-neutral test harness for **SWIM protocol** implementations — a router/coordinator written in Rust that spawns node processes and routes messages between them over stdin/stdout using newline-delimited JSON.

## Architecture

The entry point (`main.rs`) parses a JSON config, spawns one child process per node, and connects them through a shared registry. The line protocol is documented in `README.md`; the design is in the "Router control over stdio" doc.

**IPC contract:** each child reads newline-delimited JSON lines from stdin and writes them to stdout; stderr is for logs. Every line has `source`, `target`, and exactly one of `packet` (base64 frame, forwarded without decoding) or `control` (object tagged by `kind`). `__router__` is the router's address and `__all__` broadcasts a packet to every Ready node except the sender.

**Message** (`message.rs`): `Line { source, target, body: Body }` with `Body::Packet(String)` or `Body::Control(Control)`. A private `RawLine` wire type enforces "exactly one body field".

**Registry** (`registry.rs`) is an actor: a background tokio task owns each node's stdin writer and lifecycle state (Spawned or Ready; Exited nodes are removed). `RegistryHandle` is cheaply cloneable and sends `Register`, `MarkReady`, `Deregister`, `Route`, and `Broadcast` requests with `oneshot` responses. It also runs status fetches: `fetch_status` sends one `status_request` id to every Ready node, `status_reply` records each node's snapshot, and the fetch finishes when all have replied or `--status-timeout` passes. Callers that arrive mid-fetch join it, and within `--status-debounce` of the last fetch they get the cached view. Writers are boxed `AsyncWrite`s so tests can use `tokio::io::duplex`.

**Lifecycle** (`main.rs`): per node, a reader task validates stdout lines and handles `ready` and `status`; a supervisor task awaits `Child::wait()`, kills the node if `ready` does not arrive within `--ready-timeout`, and deregisters it on exit.

**Config** (`config.rs`): `implementations` (name → executable + args) and `nodes` (`id`, `implementation`, args). `Config::parse` rejects empty, duplicate, or `__`-prefixed IDs and unknown implementations.

## Key design notes

- The registry actor serializes all node state access; no locks needed.
- Nodes are identified only by string ID; the router does not decode packets or know about SWIM protocol internals.
- Not yet implemented: the admin HTTP API (the only caller of `fetch_status`), restarts, Kill/Stop, and simulated partitions.
- A slow node's stdin write (up to 5 s) blocks the registry actor, including broadcasts.
