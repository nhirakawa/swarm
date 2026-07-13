mod config;
mod message;
mod registry;

use std::path::PathBuf;
use std::process::Stdio;

use clap::{Parser, Subcommand};
use tokio::io::{AsyncBufReadExt, BufReader};

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
                let executable = config.implementations.get(&node.implementation).ok_or_else(
                    || anyhow::anyhow!("Unknown implementation '{}' for node '{}'", node.implementation, node.id),
                )?;

                let mut child = tokio::process::Command::new(executable)
                    .stdin(Stdio::piped())
                    .stdout(Stdio::piped())
                    .spawn()?;

                let stdin = child.stdin.take().expect("stdin was piped");
                let stdout = child.stdout.take().expect("stdout was piped");

                registry.register(node.id.clone(), stdin).await?;

                let registry = registry.clone();
                tokio::spawn(async move {
                    let mut lines = BufReader::new(stdout).lines();
                    while let Ok(Some(line)) = lines.next_line().await {
                        match serde_json::from_str::<message::Message>(&line) {
                            Ok(msg) => {
                                let _ = registry.route(msg).await;
                            }
                            Err(_) => {}
                        }
                    }
                });
            }

            tokio::signal::ctrl_c().await?;
        }
    }

    Ok(())
}
