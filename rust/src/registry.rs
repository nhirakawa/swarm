use std::collections::HashMap;

use tokio::process::{ChildStdin, ChildStdout};
use tokio::sync::{mpsc, oneshot};

pub type NodeId = String;

struct NodePipes {
    stdin: ChildStdin,
    stdout: ChildStdout,
}

enum RegistryRequest {
    Register {
        node_id: NodeId,
        stdin: ChildStdin,
        stdout: ChildStdout,
        response: oneshot::Sender<()>,
    },
    Deregister {
        node_id: NodeId,
        response: oneshot::Sender<bool>,
    },
    Contains {
        node_id: NodeId,
        response: oneshot::Sender<bool>,
    },
}

pub struct RegistryHandle {
    sender: mpsc::Sender<RegistryRequest>,
}

impl RegistryHandle {
    pub async fn register(
        &self,
        node_id: NodeId,
        stdin: ChildStdin,
        stdout: ChildStdout,
    ) -> anyhow::Result<()> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(RegistryRequest::Register { node_id, stdin, stdout, response: tx })
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

    pub async fn contains(&self, node_id: &NodeId) -> anyhow::Result<bool> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(RegistryRequest::Contains { node_id: node_id.clone(), response: tx })
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
    let mut nodes: HashMap<NodeId, NodePipes> = HashMap::new();

    while let Some(request) = receiver.recv().await {
        match request {
            RegistryRequest::Register { node_id, stdin, stdout, response } => {
                nodes.insert(node_id, NodePipes { stdin, stdout });
                let _ = response.send(());
            }
            RegistryRequest::Deregister { node_id, response } => {
                let existed = nodes.remove(&node_id).is_some();
                let _ = response.send(existed);
            }
            RegistryRequest::Contains { node_id, response } => {
                let _ = response.send(nodes.contains_key(&node_id));
            }
        }
    }
}
