use clap::{Parser, Subcommand};

#[derive(Debug, Parser)]
#[command(name = "swarm")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    Router,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();

    match cli.command {
        Command::Router => {
            println!("Starting IPC router...");
        }
    }

    Ok(())
}
