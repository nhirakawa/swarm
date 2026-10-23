mod admin;
mod config;
mod message;
mod nodes;
mod registry;

use std::path::PathBuf;
use std::time::Duration;

use clap::Parser;

use nodes::{NodeSettings, Supervisor};
use registry::StatusSettings;

#[derive(Debug, Parser)]
#[command(name = "swarm-router")]
struct Cli {
    #[arg(short, long)]
    config: PathBuf,

    /// Host or IP address the admin HTTP API listens on.
    #[arg(long, default_value = "127.0.0.1")]
    admin_host: String,

    /// Port the admin HTTP API listens on.
    #[arg(long, default_value_t = 8080)]
    admin_port: u16,

    /// How long a node has to send `ready` before the router kills it.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "30s")]
    ready_timeout: Duration,

    /// How long a status fetch waits for each node's reply.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "2s")]
    status_timeout: Duration,

    /// How long after a status fetch finishes that new requests are answered from its cache.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "1s")]
    status_debounce: Duration,

    /// How long Stop waits after SIGTERM before sending SIGKILL.
    #[arg(long, value_parser = humantime::parse_duration, default_value = "5s")]
    stop_grace: Duration,
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

    let supervisor = Supervisor::new(config, registry.clone(), NodeSettings {
        ready_timeout: cli.ready_timeout,
        stop_grace: cli.stop_grace,
    });

    let listener = tokio::net::TcpListener::bind((cli.admin_host.as_str(), cli.admin_port)).await?;
    eprintln!("Admin API listening on http://{}", listener.local_addr()?);

    supervisor.start_all().await?;

    let app = admin::router(supervisor, registry);
    tokio::select! {
        result = axum::serve(listener, app) => result?,
        result = tokio::signal::ctrl_c() => result?,
    }

    Ok(())
}
