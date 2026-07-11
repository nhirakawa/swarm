mod config;
mod registry;

use std::path::PathBuf;

use clap::{Parser, Subcommand};

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
            println!("{:?}", config);
        }
    }

    Ok(())
}
