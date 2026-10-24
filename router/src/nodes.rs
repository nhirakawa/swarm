use std::collections::HashMap;
use std::os::unix::process::ExitStatusExt;
use std::process::{ExitStatus, Stdio};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use nix::sys::signal::{self, Signal};
use nix::unistd::Pid;
use serde::Serialize;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::{Child, ChildStdout};
use tokio::sync::{mpsc, oneshot};
use tokio::task::JoinHandle;
use tokio::time::Instant;

use crate::config::{Config, NodeConfig};
use crate::message::{BROADCAST_ADDRESS, Body, Control, Line, ROUTER_ADDRESS};
use crate::registry::{Delivery, NodeId, RegistryHandle};

/// How long a supervisor waits for an exited node's stdout reader to drain.
const READER_DRAIN_TIMEOUT: Duration = Duration::from_secs(1);

#[derive(Debug, Clone, Copy)]
pub struct NodeSettings {
    /// How long a node has to send `ready` before it is killed.
    pub ready_timeout: Duration,
    /// How long Stop waits after SIGTERM before sending SIGKILL.
    pub stop_grace: Duration,
}

/// Why the router ended a node's process. Absent when the node exited on its own.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ExitReason {
    NeverReady,
    Killed,
    Stopped,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Exit {
    pub code: Option<i32>,
    pub signal: Option<i32>,
    pub reason: Option<ExitReason>,
}

/// One config node as the supervisor sees it, across all of its processes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NodeRecord {
    pub id: NodeId,
    pub spawns: u32,
    /// The last process's exit, or `None` while a process is running.
    pub exit: Option<Exit>,
}

#[derive(Debug)]
pub enum ActionError {
    UnknownNode,
    /// Kill or Stop on a node that has already exited.
    NotRunning,
    /// Restart on a node whose process is still running.
    NotExited,
    Spawn(anyhow::Error),
}

#[derive(Debug, Clone, Copy)]
enum Command {
    Kill,
    Stop,
}

enum Process {
    Running { commands: mpsc::Sender<Command> },
    Exited(Exit),
}

struct Slot {
    spawns: u32,
    process: Process,
}

/// Owns every config node's processes: spawns them, supervises them until they exit,
/// and carries out the admin's Restart, Kill and Stop.
#[derive(Clone)]
pub struct Supervisor {
    inner: Arc<Inner>,
}

struct Inner {
    config: Config,
    registry: RegistryHandle,
    settings: NodeSettings,
    slots: Mutex<HashMap<NodeId, Slot>>,
}

impl Supervisor {
    pub fn new(config: Config, registry: RegistryHandle, settings: NodeSettings) -> Supervisor {
        let slots = config.nodes.iter()
            .map(|node| {
                let never_spawned = Exit { code: None, signal: None, reason: None };
                (node.id.clone(), Slot { spawns: 0, process: Process::Exited(never_spawned) })
            })
            .collect();
        Supervisor { inner: Arc::new(Inner { config, registry, settings, slots: Mutex::new(slots) }) }
    }

    /// Spawns every config node, each seeded with every other config ID as a peer.
    pub async fn start_all(&self) -> anyhow::Result<()> {
        let ids: Vec<NodeId> = self.inner.config.nodes.iter().map(|n| n.id.clone()).collect();
        for id in &ids {
            let peers = ids.iter().filter(|peer| *peer != id).cloned().collect();
            self.start(id, peers).await.map_err(|e| match e {
                ActionError::Spawn(e) => e,
                e => anyhow::anyhow!("Failed to start {}: {:?}", id, e),
            })?;
        }
        Ok(())
    }

    /// Starts a fresh process for an Exited node under the same address, seeded with
    /// every Ready node as a peer.
    pub async fn restart(&self, id: &str) -> Result<(), ActionError> {
        let peers = self.inner.registry.ready_nodes().await.map_err(ActionError::Spawn)?
            .into_iter()
            .filter(|peer| peer != id)
            .collect();
        self.start(id, peers).await
    }

    /// Sends SIGKILL immediately.
    pub async fn kill(&self, id: &str) -> Result<(), ActionError> {
        self.command(id, Command::Kill).await
    }

    /// Sends SIGTERM, then SIGKILL if the node is still running after the stop grace.
    pub async fn stop(&self, id: &str) -> Result<(), ActionError> {
        self.command(id, Command::Stop).await
    }

    /// Every config node's ID, in config order.
    pub fn node_ids(&self) -> Vec<NodeId> {
        self.inner.config.nodes.iter().map(|node| node.id.clone()).collect()
    }

    /// Every config node, in config order.
    pub fn records(&self) -> Vec<NodeRecord> {
        let slots = self.inner.slots.lock().unwrap();
        self.inner.config.nodes.iter()
            .map(|node| {
                let slot = &slots[&node.id];
                let exit = match &slot.process {
                    Process::Running { .. } => None,
                    Process::Exited(exit) => Some(exit.clone()),
                };
                NodeRecord { id: node.id.clone(), spawns: slot.spawns, exit }
            })
            .collect()
    }

    async fn start(&self, id: &str, peers: Vec<NodeId>) -> Result<(), ActionError> {
        let node = self.inner.config.nodes.iter()
            .find(|node| node.id == id)
            .ok_or(ActionError::UnknownNode)?;

        let (commands_tx, commands_rx) = mpsc::channel(4);
        {
            let mut slots = self.inner.slots.lock().unwrap();
            let slot = slots.get_mut(id).ok_or(ActionError::UnknownNode)?;
            if matches!(slot.process, Process::Running { .. }) {
                return Err(ActionError::NotExited);
            }
            slot.spawns += 1;
            slot.process = Process::Running { commands: commands_tx };
        }

        if let Err(e) = self.spawn_process(node, peers, commands_rx).await {
            eprintln!("Failed to spawn {}: {}", id, e);
            self.set_exited(id, Exit { code: None, signal: None, reason: None });
            return Err(ActionError::Spawn(e));
        }
        Ok(())
    }

    async fn command(&self, id: &str, command: Command) -> Result<(), ActionError> {
        let commands = {
            let slots = self.inner.slots.lock().unwrap();
            match &slots.get(id).ok_or(ActionError::UnknownNode)?.process {
                Process::Running { commands } => commands.clone(),
                Process::Exited(_) => return Err(ActionError::NotRunning),
            }
        };
        // The supervisor holds the receiver until it marks the node Exited, so a failed
        // send means the process exited after the check above.
        commands.send(command).await.map_err(|_| ActionError::NotRunning)
    }

    fn set_exited(&self, id: &str, exit: Exit) {
        if let Some(slot) = self.inner.slots.lock().unwrap().get_mut(id) {
            slot.process = Process::Exited(exit);
        }
    }

    async fn spawn_process(
        &self,
        node: &NodeConfig,
        peers: Vec<NodeId>,
        commands: mpsc::Receiver<Command>,
    ) -> anyhow::Result<()> {
        let implementation = &self.inner.config.implementations[&node.implementation];

        let mut merged_args = implementation.args.clone();
        merged_args.extend(node.args.clone());

        let mut cmd = tokio::process::Command::new(&implementation.executable);
        cmd.stdin(Stdio::piped()).stdout(Stdio::piped()).kill_on_drop(true);
        cmd.arg("--address").arg(&node.id);
        for (key, value) in &merged_args {
            cmd.arg(format!("--{}", key)).arg(value);
        }
        for peer in peers {
            cmd.arg("--peer").arg(peer);
        }

        let mut child = cmd.spawn()?;

        let stdin = child.stdin.take().expect("stdin was piped");
        let stdout = child.stdout.take().expect("stdout was piped");

        self.inner.registry.register(node.id.clone(), Box::new(stdin)).await?;

        let (ready_tx, ready_rx) = oneshot::channel();
        let reader = tokio::spawn(read_lines(node.id.clone(), stdout, self.inner.registry.clone(), ready_tx));

        tokio::spawn(self.clone().supervise(node.id.clone(), child, ready_rx, commands, reader));

        Ok(())
    }

    /// Waits for the node's process to exit, carrying out Kill and Stop and killing the
    /// node if it does not send `ready` in time, then deregisters it and marks it Exited.
    async fn supervise(
        self,
        node_id: NodeId,
        mut child: Child,
        mut ready_rx: oneshot::Receiver<()>,
        mut commands: mpsc::Receiver<Command>,
        reader: JoinHandle<()>,
    ) {
        let settings = self.inner.settings;
        let ready_deadline = tokio::time::sleep(settings.ready_timeout);
        tokio::pin!(ready_deadline);

        let mut ready = false;
        let mut ready_settled = false;
        let mut reason = None;
        let mut kill_at: Option<Instant> = None;

        let status = loop {
            tokio::select! {
                status = child.wait() => break status,
                result = &mut ready_rx, if !ready_settled => {
                    ready_settled = true;
                    ready = result.is_ok();
                }
                _ = &mut ready_deadline, if !ready => {
                    eprintln!("Node {} did not send ready within {:?}; killing it", node_id, settings.ready_timeout);
                    reason = Some(ExitReason::NeverReady);
                    let _ = child.start_kill();
                    ready = true; // disarm the deadline
                }
                Some(command) = commands.recv() => match command {
                    Command::Kill => {
                        eprintln!("Killing node {}", node_id);
                        reason = Some(ExitReason::Killed);
                        let _ = child.start_kill();
                    }
                    Command::Stop if kill_at.is_none() && reason.is_none() => {
                        eprintln!("Stopping node {}", node_id);
                        reason = Some(ExitReason::Stopped);
                        if let Some(pid) = child.id() {
                            let _ = signal::kill(Pid::from_raw(pid as i32), Signal::SIGTERM);
                        }
                        kill_at = Some(Instant::now() + settings.stop_grace);
                    }
                    Command::Stop => {}
                },
                _ = tokio::time::sleep_until(kill_at.unwrap_or_else(Instant::now)), if kill_at.is_some() => {
                    eprintln!("Node {} did not exit within {:?} of SIGTERM; killing it", node_id, settings.stop_grace);
                    kill_at = None;
                    let _ = child.start_kill();
                }
            }
        };

        let exit = match status {
            Ok(status) => {
                eprintln!("Node {} exited: {}", node_id, status);
                exit_from(status, reason)
            }
            Err(e) => {
                eprintln!("Failed to wait for node {}: {}", node_id, e);
                Exit { code: None, signal: None, reason }
            }
        };

        // Drain the old process's stdout before deregistering, so none of its lines are
        // handled after a restart reuses the address.
        let abort = reader.abort_handle();
        if tokio::time::timeout(READER_DRAIN_TIMEOUT, reader).await.is_err() {
            abort.abort();
        }

        let _ = self.inner.registry.deregister(node_id.clone()).await;
        self.set_exited(&node_id, exit);
    }
}

fn exit_from(status: ExitStatus, reason: Option<ExitReason>) -> Exit {
    Exit { code: status.code(), signal: status.signal(), reason }
}

async fn read_lines(node_id: NodeId, stdout: ChildStdout, registry: RegistryHandle, ready_tx: oneshot::Sender<()>) {
    let mut ready_tx = Some(ready_tx);
    let mut lines = BufReader::new(stdout).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        handle_line(&node_id, &line, &registry, &mut ready_tx).await;
    }
}

/// Validates one stdout line from `node_id` and routes, broadcasts or handles it.
async fn handle_line(
    node_id: &str,
    raw: &str,
    registry: &RegistryHandle,
    ready_tx: &mut Option<oneshot::Sender<()>>,
) {
    let line: Line = match serde_json::from_str(raw) {
        Ok(line) => line,
        Err(e) => {
            eprintln!("Dropping line from {}: {}", node_id, e);
            return;
        }
    };

    if line.source != node_id {
        eprintln!("Dropping line from {}: source is '{}'", node_id, line.source);
        return;
    }

    match (&line.body, line.target.as_str()) {
        (Body::Control(control), ROUTER_ADDRESS) => {
            handle_control(node_id, control, registry, ready_tx).await;
        }
        (Body::Control(_), target) => {
            eprintln!("Dropping control from {}: addressed to '{}'", node_id, target);
        }
        (Body::Packet(_), ROUTER_ADDRESS) => {
            eprintln!("Dropping packet from {}: addressed to the router", node_id);
        }
        (Body::Packet(_), BROADCAST_ADDRESS) => {
            let _ = registry.broadcast(line).await;
        }
        (Body::Packet(_), _) => {
            let target = line.target.clone();
            match registry.route(line).await {
                // Drops on blocked links are counted, not logged: a partition drops every packet.
                Ok(Delivery::Delivered | Delivery::Blocked) => {}
                Ok(Delivery::UnknownTarget) => {
                    eprintln!("Dropping packet from {}: unknown target '{}'", node_id, target);
                }
                Ok(Delivery::WriteFailed) | Err(_) => {
                    eprintln!("Failed to deliver packet from {} to {}", node_id, target);
                }
            }
        }
    }
}

async fn handle_control(
    node_id: &str,
    control: &Control,
    registry: &RegistryHandle,
    ready_tx: &mut Option<oneshot::Sender<()>>,
) {
    match control {
        Control::Ready => {
            if registry.mark_ready(node_id.to_string()).await.unwrap_or(false) {
                eprintln!("Node {} is ready", node_id);
                if let Some(tx) = ready_tx.take() {
                    let _ = tx.send(());
                }
            } else {
                eprintln!("Ignoring repeated ready from {}", node_id);
            }
        }
        Control::Status { id, snapshot } => {
            if !registry.status_reply(node_id.to_string(), *id, snapshot.clone()).await.unwrap_or(false) {
                eprintln!("Ignoring status {} from {}: no request pending", id, node_id);
            }
        }
        Control::StatusRequest { .. } => {
            eprintln!("Dropping status_request from {}: only the router sends it", node_id);
        }
        Control::Unknown => {}
    }
}

#[cfg(test)]
pub mod test_support {
    use std::os::unix::fs::PermissionsExt;

    use serde_json::Value;

    use super::*;
    use crate::registry::{self, StatusSettings};

    /// A node that follows the contract: sends `ready` (unless `--ready no`), answers
    /// `status_request` with its address and peers, and exits when stdin closes.
    /// `--ignore-term yes` makes it ignore SIGTERM.
    const FAKE_NODE: &str = r#"#!/bin/sh
address=""; peers=""; ready=yes; ignore_term=no
while [ $# -gt 0 ]; do
  case "$1" in
    --address) address="$2" ;;
    --peer) peers="$peers $2" ;;
    --ready) ready="$2" ;;
    --ignore-term) ignore_term="$2" ;;
  esac
  shift 2
done
[ "$ignore_term" = yes ] && trap '' TERM
[ "$ready" = yes ] && echo "{\"source\":\"$address\",\"target\":\"__router__\",\"control\":{\"kind\":\"ready\"}}"
while IFS= read -r line; do
  case "$line" in
    *status_request*)
      id=$(echo "$line" | sed 's/.*"id":\([0-9]*\).*/\1/')
      echo "{\"source\":\"$address\",\"target\":\"__router__\",\"control\":{\"kind\":\"status\",\"id\":$id,\"snapshot\":{\"peers\":\"${peers# }\"}}}"
      ;;
  esac
done
"#;

    pub const SETTINGS: NodeSettings = NodeSettings {
        ready_timeout: Duration::from_millis(500),
        stop_grace: Duration::from_millis(300),
    };

    pub struct Harness {
        pub supervisor: Supervisor,
        pub registry: RegistryHandle,
        _dir: tempfile::TempDir,
    }

    /// Starts a supervisor over the fake node; each entry is a node ID and its extra args.
    pub async fn start(nodes: &[(&str, &[(&str, &str)])]) -> Harness {
        let dir = tempfile::tempdir().unwrap();
        let script = dir.path().join("fake-node.sh");
        std::fs::write(&script, FAKE_NODE).unwrap();
        std::fs::set_permissions(&script, std::fs::Permissions::from_mode(0o755)).unwrap();

        let nodes: Vec<Value> = nodes.iter()
            .map(|(id, args)| {
                let args: serde_json::Map<String, Value> = args.iter()
                    .map(|(k, v)| (k.to_string(), Value::from(*v)))
                    .collect();
                serde_json::json!({"id": id, "implementation": "fake", "args": args})
            })
            .collect();
        let raw = serde_json::json!({
            "implementations": {"fake": {"executable": script}},
            "nodes": nodes,
        });
        let config = Config::parse(&raw.to_string()).unwrap();

        let registry = registry::start(StatusSettings {
            timeout: Duration::from_secs(2),
            debounce: Duration::ZERO,
        });
        let supervisor = Supervisor::new(config, registry.clone(), SETTINGS);
        supervisor.start_all().await.unwrap();
        Harness { supervisor, registry, _dir: dir }
    }

    impl Harness {
        pub fn record(&self, id: &str) -> NodeRecord {
            self.supervisor.records().into_iter().find(|r| r.id == id).unwrap()
        }

        /// Waits until the node's latest process has exited and returns its exit.
        pub async fn exited(&self, id: &str) -> Exit {
            self.eventually(|| self.record(id).exit).await
        }

        pub async fn ready(&self, id: &str) {
            let ready = || async { self.registry.ready_nodes().await.unwrap().contains(&id.to_string()) };
            let deadline = Instant::now() + Duration::from_secs(5);
            while !ready().await {
                assert!(Instant::now() < deadline, "{} never became ready", id);
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }

        async fn eventually<T>(&self, check: impl Fn() -> Option<T>) -> T {
            let deadline = Instant::now() + Duration::from_secs(5);
            loop {
                if let Some(value) = check() {
                    return value;
                }
                assert!(Instant::now() < deadline, "condition never held");
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use serde_json::json;

    use super::test_support::{SETTINGS, start};
    use super::*;

    const SIGKILL: i32 = 9;
    const SIGTERM: i32 = 15;

    #[tokio::test]
    async fn kill_then_restart_reuses_the_address_with_ready_peers() {
        let harness = start(&[("node-1", &[]), ("node-2", &[]), ("node-3", &[])]).await;
        for id in ["node-1", "node-2", "node-3"] {
            harness.ready(id).await;
        }

        harness.supervisor.kill("node-3").await.unwrap();
        let exit = harness.exited("node-3").await;
        assert_eq!(exit, Exit { code: None, signal: Some(SIGKILL), reason: Some(ExitReason::Killed) });

        harness.supervisor.kill("node-1").await.unwrap();
        harness.exited("node-1").await;

        harness.supervisor.restart("node-1").await.unwrap();
        harness.ready("node-1").await;
        let record = harness.record("node-1");
        assert_eq!(record.spawns, 2);
        assert_eq!(record.exit, None);

        let statuses = harness.registry.fetch_status().await.unwrap();
        let snapshot = &statuses["node-1"].snapshot.as_ref().unwrap().value;
        assert_eq!(snapshot, &json!({"peers": "node-2"}));
    }

    #[tokio::test]
    async fn stop_sends_sigterm() {
        let harness = start(&[("node-1", &[])]).await;
        harness.ready("node-1").await;

        harness.supervisor.stop("node-1").await.unwrap();
        let exit = harness.exited("node-1").await;
        assert_eq!(exit, Exit { code: None, signal: Some(SIGTERM), reason: Some(ExitReason::Stopped) });
    }

    #[tokio::test]
    async fn stop_kills_a_node_that_ignores_sigterm_after_the_grace() {
        let harness = start(&[("node-1", &[("ignore-term", "yes")])]).await;
        harness.ready("node-1").await;

        let started = Instant::now();
        harness.supervisor.stop("node-1").await.unwrap();
        let exit = harness.exited("node-1").await;
        assert!(started.elapsed() >= SETTINGS.stop_grace);
        assert_eq!(exit, Exit { code: None, signal: Some(SIGKILL), reason: Some(ExitReason::Stopped) });
    }

    #[tokio::test]
    async fn node_that_never_sends_ready_is_killed() {
        let harness = start(&[("node-1", &[("ready", "no")])]).await;

        let exit = harness.exited("node-1").await;
        assert_eq!(exit, Exit { code: None, signal: Some(SIGKILL), reason: Some(ExitReason::NeverReady) });
    }

    #[tokio::test]
    async fn actions_check_the_node_state() {
        let harness = start(&[("node-1", &[])]).await;
        harness.ready("node-1").await;

        assert!(matches!(harness.supervisor.restart("node-1").await, Err(ActionError::NotExited)));
        assert!(matches!(harness.supervisor.kill("node-9").await, Err(ActionError::UnknownNode)));
        assert!(matches!(harness.supervisor.restart("node-9").await, Err(ActionError::UnknownNode)));

        harness.supervisor.kill("node-1").await.unwrap();
        harness.exited("node-1").await;
        assert!(matches!(harness.supervisor.kill("node-1").await, Err(ActionError::NotRunning)));
        assert!(matches!(harness.supervisor.stop("node-1").await, Err(ActionError::NotRunning)));
    }
}
