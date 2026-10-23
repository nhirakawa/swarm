mod config;
mod message;
mod registry;

use std::path::PathBuf;
use std::process::Stdio;
use std::time::Duration;

use clap::Parser;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Child;
use tokio::sync::oneshot;

use message::{BROADCAST_ADDRESS, Body, Control, Line, ROUTER_ADDRESS};
use registry::{Delivery, RegistryHandle, StatusSettings};

#[derive(Debug, Parser)]
#[command(name = "swarm-router")]
struct Cli {
    #[arg(short, long)]
    config: PathBuf,

    /// How long a node has to send `ready` before the router kills it.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "30s")]
    ready_timeout: Duration,

    /// How long a status fetch waits for each node's reply.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "2s")]
    status_timeout: Duration,

    /// How long after a status fetch finishes that new requests are answered from its cache.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "1s")]
    status_debounce: Duration,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    let raw = std::fs::read_to_string(&cli.config)?;
    let config = config::Config::parse(&raw)?;

    let registry = registry::start(StatusSettings {
        timeout: cli.status_timeout,
        debounce: cli.status_debounce,
    });

    for node in &config.nodes {
        spawn_node(&config, node, &registry, cli.ready_timeout).await?;
    }

    tokio::signal::ctrl_c().await?;

    Ok(())
}

async fn spawn_node(
    config: &config::Config,
    node: &config::NodeConfig,
    registry: &RegistryHandle,
    ready_timeout: Duration,
) -> anyhow::Result<()> {
    let implementation = &config.implementations[&node.implementation];

    let peers: Vec<&str> = config.nodes.iter()
        .filter(|n| n.id != node.id)
        .map(|n| n.id.as_str())
        .collect();

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

    registry.register(node.id.clone(), Box::new(stdin)).await?;

    let (ready_tx, ready_rx) = oneshot::channel();

    let node_id = node.id.clone();
    let reader_registry = registry.clone();
    tokio::spawn(async move {
        let mut ready_tx = Some(ready_tx);
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            handle_line(&node_id, &line, &reader_registry, &mut ready_tx).await;
        }
    });

    tokio::spawn(supervise(node.id.clone(), child, ready_rx, ready_timeout, registry.clone()));

    Ok(())
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
                Ok(Delivery::Delivered) => {}
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

/// Waits for the node's process to exit, killing it first if it does not send
/// `ready` within `ready_timeout`, and then deregisters it.
async fn supervise(
    node_id: String,
    mut child: Child,
    ready_rx: oneshot::Receiver<()>,
    ready_timeout: Duration,
    registry: RegistryHandle,
) {
    let status = tokio::select! {
        status = child.wait() => status,
        Ok(()) = ready_rx => child.wait().await,
        _ = tokio::time::sleep(ready_timeout) => {
            eprintln!("Node {} did not send ready within {:?}; killing it", node_id, ready_timeout);
            let _ = child.kill().await;
            child.wait().await
        }
    };

    match status {
        Ok(status) => eprintln!("Node {} exited: {}", node_id, status),
        Err(e) => eprintln!("Failed to wait for node {}: {}", node_id, e),
    }

    let _ = registry.deregister(node_id).await;
}
