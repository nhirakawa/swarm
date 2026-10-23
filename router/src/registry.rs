use std::collections::HashMap;
use std::time::Duration;

use tokio::io::{AsyncWrite, AsyncWriteExt};
use tokio::sync::{mpsc, oneshot};

use crate::message::Line;

pub type NodeId = String;

/// A node's stdin, boxed so tests can substitute an in-memory pipe.
pub type NodeWriter = Box<dyn AsyncWrite + Send + Unpin>;

const WRITE_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NodeState {
    Spawned,
    Ready,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Delivery {
    Delivered,
    UnknownTarget,
    WriteFailed,
}

struct Node {
    writer: NodeWriter,
    state: NodeState,
}

enum RegistryRequest {
    Register {
        node_id: NodeId,
        writer: NodeWriter,
        response: oneshot::Sender<()>,
    },
    MarkReady {
        node_id: NodeId,
        response: oneshot::Sender<bool>,
    },
    Deregister {
        node_id: NodeId,
        response: oneshot::Sender<bool>,
    },
    Route {
        line: Line,
        response: oneshot::Sender<Delivery>,
    },
    Broadcast {
        line: Line,
        response: oneshot::Sender<usize>,
    },
}

#[derive(Clone)]
pub struct RegistryHandle {
    sender: mpsc::Sender<RegistryRequest>,
}

impl RegistryHandle {
    pub async fn register(&self, node_id: NodeId, writer: NodeWriter) -> anyhow::Result<()> {
        self.request(|response| RegistryRequest::Register { node_id, writer, response }).await
    }

    /// Moves a Spawned node to Ready. Returns false if the node is unknown or already Ready.
    pub async fn mark_ready(&self, node_id: NodeId) -> anyhow::Result<bool> {
        self.request(|response| RegistryRequest::MarkReady { node_id, response }).await
    }

    pub async fn deregister(&self, node_id: NodeId) -> anyhow::Result<bool> {
        self.request(|response| RegistryRequest::Deregister { node_id, response }).await
    }

    /// Writes the line to the stdin of the node named by its target.
    pub async fn route(&self, line: Line) -> anyhow::Result<Delivery> {
        self.request(|response| RegistryRequest::Route { line, response }).await
    }

    /// Writes the line to every Ready node except its source. Returns how many writes succeeded.
    pub async fn broadcast(&self, line: Line) -> anyhow::Result<usize> {
        self.request(|response| RegistryRequest::Broadcast { line, response }).await
    }

    async fn request<T>(
        &self,
        build: impl FnOnce(oneshot::Sender<T>) -> RegistryRequest,
    ) -> anyhow::Result<T> {
        let (tx, rx) = oneshot::channel();
        self.sender
            .send(build(tx))
            .await
            .map_err(|_| anyhow::anyhow!("registry has stopped"))?;
        Ok(rx.await?)
    }
}

pub fn start() -> RegistryHandle {
    let (sender, receiver) = mpsc::channel(64);
    tokio::spawn(run(receiver));
    RegistryHandle { sender }
}

async fn run(mut receiver: mpsc::Receiver<RegistryRequest>) {
    let mut nodes: HashMap<NodeId, Node> = HashMap::new();

    while let Some(request) = receiver.recv().await {
        match request {
            RegistryRequest::Register { node_id, writer, response } => {
                nodes.insert(node_id, Node { writer, state: NodeState::Spawned });
                let _ = response.send(());
            }
            RegistryRequest::MarkReady { node_id, response } => {
                let transitioned = match nodes.get_mut(&node_id) {
                    Some(node) if node.state == NodeState::Spawned => {
                        node.state = NodeState::Ready;
                        true
                    }
                    _ => false,
                };
                let _ = response.send(transitioned);
            }
            RegistryRequest::Deregister { node_id, response } => {
                let _ = response.send(nodes.remove(&node_id).is_some());
            }
            RegistryRequest::Route { line, response } => {
                let delivery = match nodes.get_mut(&line.target) {
                    Some(node) => {
                        if write_line(&mut node.writer, &line).await {
                            Delivery::Delivered
                        } else {
                            Delivery::WriteFailed
                        }
                    }
                    None => Delivery::UnknownTarget,
                };
                let _ = response.send(delivery);
            }
            RegistryRequest::Broadcast { line, response } => {
                let mut delivered = 0;
                for (node_id, node) in nodes.iter_mut() {
                    if *node_id == line.source || node.state != NodeState::Ready {
                        continue;
                    }
                    if write_line(&mut node.writer, &line).await {
                        delivered += 1;
                    } else {
                        eprintln!("Broadcast from {} to {} failed", line.source, node_id);
                    }
                }
                let _ = response.send(delivered);
            }
        }
    }
}

async fn write_line(writer: &mut NodeWriter, line: &Line) -> bool {
    let mut encoded = serde_json::to_string(line).expect("lines always serialize");
    encoded.push('\n');
    tokio::time::timeout(WRITE_TIMEOUT, writer.write_all(encoded.as_bytes()))
        .await
        .is_ok_and(|r| r.is_ok())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::message::{BROADCAST_ADDRESS, Body};
    use tokio::io::{AsyncBufReadExt, BufReader, DuplexStream, Lines};

    async fn register(registry: &RegistryHandle, id: &str) -> Lines<BufReader<DuplexStream>> {
        let (writer, reader) = tokio::io::duplex(4096);
        registry.register(id.to_string(), Box::new(writer)).await.unwrap();
        BufReader::new(reader).lines()
    }

    fn packet(source: &str, target: &str) -> Line {
        Line {
            source: source.to_string(),
            target: target.to_string(),
            body: Body::Packet("AAIA".to_string()),
        }
    }

    #[tokio::test]
    async fn routes_to_target() {
        let registry = start();
        let mut node_2 = register(&registry, "node-2").await;

        let line = packet("node-1", "node-2");
        assert_eq!(registry.route(line.clone()).await.unwrap(), Delivery::Delivered);

        let received: Line = serde_json::from_str(&node_2.next_line().await.unwrap().unwrap()).unwrap();
        assert_eq!(received, line);
    }

    #[tokio::test]
    async fn reports_unknown_target() {
        let registry = start();
        assert_eq!(registry.route(packet("node-1", "node-9")).await.unwrap(), Delivery::UnknownTarget);
    }

    #[tokio::test]
    async fn broadcasts_to_ready_nodes_except_sender() {
        let registry = start();
        let _sender = register(&registry, "node-1").await;
        let mut ready = register(&registry, "node-2").await;
        let _spawned = register(&registry, "node-3").await;

        assert!(registry.mark_ready("node-1".to_string()).await.unwrap());
        assert!(registry.mark_ready("node-2".to_string()).await.unwrap());

        let delivered = registry.broadcast(packet("node-1", BROADCAST_ADDRESS)).await.unwrap();
        assert_eq!(delivered, 1);
        assert!(ready.next_line().await.unwrap().is_some());
    }

    #[tokio::test]
    async fn mark_ready_only_transitions_once() {
        let registry = start();
        let _node = register(&registry, "node-1").await;

        assert!(registry.mark_ready("node-1".to_string()).await.unwrap());
        assert!(!registry.mark_ready("node-1".to_string()).await.unwrap());
        assert!(!registry.mark_ready("node-9".to_string()).await.unwrap());
    }

    #[tokio::test]
    async fn deregistered_nodes_are_unknown() {
        let registry = start();
        let _node = register(&registry, "node-2").await;

        assert!(registry.deregister("node-2".to_string()).await.unwrap());
        assert_eq!(registry.route(packet("node-1", "node-2")).await.unwrap(), Delivery::UnknownTarget);
    }
}
