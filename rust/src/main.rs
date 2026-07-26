mod config;
mod message;
mod registry;

use std::path::PathBuf;
use std::process::Stdio;
use std::time::Duration;

use clap::{Parser, Subcommand};
use tokio::io::{AsyncBufReadExt, BufReader};

const MULTICAST_TARGET: &str = "MULTICAST";
const ROUTER_SOURCE: &str = "__router__";
const STATUS_POLL_INTERVAL: Duration = Duration::from_secs(5);

#[derive(Debug, Parser)]
#[command(name = "swarm")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    Router {
        #[arg(short, long)]
        config: PathBuf,
    },
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    match cli.command {
        Command::Router { config } => {
            let raw = std::fs::read_to_string(&config)?;
            let config: config::Config = serde_json::from_str(&raw)?;

            let registry = registry::start();

            for node in &config.nodes {
                spawn_node(&config, node, &registry).await?;
            }

            for node in &config.nodes {
                start_status_poller(node.id.clone(), node.admin_port, registry.clone());
            }

            tokio::signal::ctrl_c().await?;
        }
    }

    Ok(())
}

async fn spawn_node(
    config: &config::Config,
    node: &config::NodeConfig,
    registry: &registry::RegistryHandle,
) -> anyhow::Result<()> {
    let implementation = config.implementations.get(&node.implementation).ok_or_else(
        || anyhow::anyhow!("Unknown implementation '{}' for node '{}'", node.implementation, node.id),
    )?;

    let peers: Vec<&str> = config.nodes.iter()
        .filter(|n| n.id != node.id)
        .map(|n| n.id.as_str())
        .collect();

    let mut merged_args = implementation.args.clone();
    merged_args.extend(node.args.clone());

    let mut cmd = tokio::process::Command::new(&implementation.executable);
    cmd.stdin(Stdio::piped()).stdout(Stdio::piped());
    cmd.arg("--address").arg(&node.id);
    cmd.arg("--admin-port").arg(node.admin_port.to_string());
    for (key, value) in &merged_args {
        cmd.arg(format!("--{}", key)).arg(value);
    }
    for peer in peers {
        cmd.arg("--peer").arg(peer);
    }

    let mut child = cmd.spawn()?;

    let stdin = child.stdin.take().expect("stdin was piped");
    let stdout = child.stdout.take().expect("stdout was piped");

    registry.register(node.id.clone(), stdin).await?;

    let registry = registry.clone();
    tokio::spawn(async move {
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if let Ok(msg) = serde_json::from_str::<message::Message>(&line) {
                if msg.target == MULTICAST_TARGET {
                    handle_multicast(msg.source, &registry).await;
                } else {
                    let _ = registry.route(msg).await;
                }
            }
        }
    });

    Ok(())
}

async fn handle_multicast(requester: String, registry: &registry::RegistryHandle) {
    let active = registry.list_active().await.unwrap_or_default();
    let payload = serde_json::to_string(&active).unwrap_or_default();
    let response = message::Message {
        source: ROUTER_SOURCE.to_string(),
        target: requester,
        payload,
    };
    let _ = registry.route(response).await;
}

fn start_status_poller(node_id: String, status_port: u16, registry: registry::RegistryHandle) {
    tokio::spawn(async move {
        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/status", status_port);
        loop {
            let active = client.get(&url)
                .timeout(Duration::from_secs(2))
                .send()
                .await
                .is_ok_and(|r| r.status().is_success());
            let _ = registry.update_active(node_id.clone(), active).await;
            tokio::time::sleep(STATUS_POLL_INTERVAL).await;
        }
    });
}
