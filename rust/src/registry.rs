use std::collections::HashMap;

use tokio::io::AsyncWriteExt;
use tokio::process::ChildStdin;
use tokio::sync::{mpsc, oneshot};

use crate::message::Message;

pub type NodeId = String;

enum RegistryRequest {
    Register {
        node_id: NodeId,
        stdin: ChildStdin,
        response: oneshot::Sender<()>,
    },
    Deregister {
        node_id: NodeId,
        response: oneshot::Sender<bool>,
    },
    Route {
        message: Message,
        response: oneshot::Sender<bool>,
    },
}

#[derive(Clone)]
pub struct RegistryHandle {
    sender: mpsc::Sender<RegistryRequest>,
}

impl RegistryHandle {
    pub async fn register(&self, node_id: NodeId, stdin: ChildStdin) -> anyhow::Result<()> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(RegistryRequest::Register { node_id, stdin, response: tx })
            .await?;
        rx.await?;
        Ok(())
    }

    pub async fn deregister(&self, node_id: NodeId) -> anyhow::Result<bool> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(RegistryRequest::Deregister { node_id, response: tx })
            .await?;
        Ok(rx.await?)
    }

    pub async fn route(&self, message: Message) -> anyhow::Result<bool> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(RegistryRequest::Route { message, response: tx })
            .await?;
        Ok(rx.await?)
    }
}

pub fn start() -> RegistryHandle {
    let (sender, receiver) = mpsc::channel(64);
    tokio::spawn(run(receiver));
    RegistryHandle { sender }
}

async fn run(mut receiver: mpsc::Receiver<RegistryRequest>) {
    let mut nodes: HashMap<NodeId, ChildStdin> = HashMap::new();

    while let Some(request) = receiver.recv().await {
        match request {
            RegistryRequest::Register { node_id, stdin, response } => {
                nodes.insert(node_id, stdin);
                let _ = response.send(());
            }
            RegistryRequest::Deregister { node_id, response } => {
                let existed = nodes.remove(&node_id).is_some();
                let _ = response.send(existed);
            }
            RegistryRequest::Route { message, response } => {
                if let Some(stdin) = nodes.get_mut(&message.target) {
                    let mut line = serde_json::to_string(&message).unwrap();
                    line.push('\n');
                    let delivered = stdin.write_all(line.as_bytes()).await.is_ok();
                    let _ = response.send(delivered);
                } else {
                    let _ = response.send(false);
                }
            }
        }
    }
}
