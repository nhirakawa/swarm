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

The entry point (`main.rs`) parses a JSON config, starts the registry and the node supervisor, spawns every node, and serves the admin API. The line protocol is documented in `README.md`; the design is in the "Router control over stdio" doc.

**IPC contract:** each child reads newline-delimited JSON lines from stdin and writes them to stdout; stderr is for logs. Every line has `source`, `target`, and exactly one of `packet` (base64 frame, forwarded without decoding) or `control` (object tagged by `kind`). `__router__` is the router's address and `__all__` broadcasts a packet to every Ready node except the sender.

**Message** (`message.rs`): `Line { source, target, body: Body }` with `Body::Packet(String)` or `Body::Control(Control)`. A private `RawLine` wire type enforces "exactly one body field".

**Registry** (`registry.rs`) is an actor: a background tokio task owns each node's stdin writer and lifecycle state (Spawned or Ready; Exited nodes are removed). `RegistryHandle` is cheaply cloneable and sends `Register`, `MarkReady`, `Deregister`, `Route`, and `Broadcast` requests with `oneshot` responses. It also runs status fetches: `fetch_status` sends one `status_request` id to every Ready node, `status_reply` records each node's snapshot, and the fetch finishes when all have replied or `--status-timeout` passes. Callers that arrive mid-fetch join it, and within `--status-debounce` of the last fetch they get the cached view. Writers are boxed `AsyncWrite`s so tests can use `tokio::io::duplex`.

**Nodes** (`nodes.rs`): `Supervisor` owns each config node's slot (spawn count, and Running with a command channel or Exited with its exit) behind a `Mutex`. Per process, a reader task validates stdout lines and handles `ready` and `status`; a supervise task awaits `Child::wait()`, carries out Kill (SIGKILL) and Stop (SIGTERM, then SIGKILL after `--stop-grace`), kills the node if `ready` does not arrive within `--ready-timeout`, drains the reader, deregisters the node, and only then marks it Exited, so a restart never races the old process. Restart needs an Exited node and seeds it with the Ready nodes as `--peer`; the initial spawn seeds every other config ID. Tests drive a fake node shell script from `nodes::test_support`.

**Admin** (`admin.rs`): axum routes `GET /` (the UI, a self-contained page embedded from `assets/admin.html` with `include_str!`, which keeps fetch history client-side), `GET /nodes` (calls `fetch_status` and merges it with the supervisor's records) and `POST /nodes/{id}/{restart,kill,stop}`, which return 202, 404 or 409.

**Config** (`config.rs`): `implementations` (name → executable + args) and `nodes` (`id`, `implementation`, args). `Config::parse` rejects empty, duplicate, or `__`-prefixed IDs and unknown implementations.

## Key design notes

- The registry actor serializes routing and status state; the supervisor's slots are the only lock, never held across an await.
- Nodes are identified only by string ID; the router does not decode packets or know about SWIM protocol internals.
- Not yet implemented: simulated partitions.
- A slow node's stdin write (up to 5 s) blocks the registry actor, including broadcasts.
