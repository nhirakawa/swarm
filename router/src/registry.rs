use std::collections::{BTreeMap, HashMap, HashSet};
use std::time::Duration;

use serde_json::Value;
use tokio::io::{AsyncWrite, AsyncWriteExt};
use tokio::sync::{mpsc, oneshot};
use tokio::time::Instant;

use crate::message::{Body, Control, Line, ROUTER_ADDRESS};

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

#[derive(Debug, Clone, Copy)]
pub struct StatusSettings {
    /// How long a fetch waits for each node's `status` reply.
    pub timeout: Duration,
    /// How long after a fetch finishes that new requests are answered from the cache.
    pub debounce: Duration,
}

/// A node's latest `status` reply and when it arrived.
#[derive(Debug, Clone, PartialEq)]
pub struct Snapshot {
    pub value: Value,
    pub fetched_at: Instant,
}

#[derive(Debug, Clone, PartialEq)]
pub struct NodeStatus {
    pub state: NodeState,
    /// Whether the node replied to the fetch this status comes from.
    pub replied: bool,
    /// The node's latest snapshot, from an earlier fetch if it did not reply to this one.
    pub snapshot: Option<Snapshot>,
}

pub type StatusView = BTreeMap<NodeId, NodeStatus>;

struct Node {
    writer: NodeWriter,
    state: NodeState,
    snapshot: Option<Snapshot>,
}

/// A fetch in flight: one `status_request` id sent to every Ready node at once.
struct Fetch {
    id: u64,
    outstanding: HashSet<NodeId>,
    replied: HashSet<NodeId>,
    waiters: Vec<oneshot::Sender<StatusView>>,
}

struct FinishedFetch {
    finished_at: Instant,
    replied: HashSet<NodeId>,
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
    ReadyNodes {
        response: oneshot::Sender<Vec<NodeId>>,
    },
    FetchStatus {
        response: oneshot::Sender<StatusView>,
    },
    StatusReply {
        node_id: NodeId,
        id: u64,
        snapshot: Value,
        response: oneshot::Sender<bool>,
    },
    /// Sent by a fetch's timer once `StatusSettings::timeout` has passed.
    FetchTimedOut {
        id: u64,
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

    /// Returns the IDs of every Ready node, sorted.
    pub async fn ready_nodes(&self) -> anyhow::Result<Vec<NodeId>> {
        self.request(|response| RegistryRequest::ReadyNodes { response }).await
    }

    /// Sends `status_request` to every Ready node and waits for their replies or the
    /// timeout. Joins a fetch already in flight, and answers from the cache within the
    /// debounce window of the last one.
    pub async fn fetch_status(&self) -> anyhow::Result<StatusView> {
        self.request(|response| RegistryRequest::FetchStatus { response }).await
    }

    /// Records a node's `status` reply. Returns false if no fetch is waiting for it.
    pub async fn status_reply(&self, node_id: NodeId, id: u64, snapshot: Value) -> anyhow::Result<bool> {
        self.request(|response| RegistryRequest::StatusReply { node_id, id, snapshot, response }).await
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

pub fn start(status_settings: StatusSettings) -> RegistryHandle {
    let (sender, receiver) = mpsc::channel(64);
    // Fetch timers hold a weak sender, so the registry still stops once every handle is dropped.
    let registry = Registry {
        nodes: HashMap::new(),
        status_settings,
        timer_sender: sender.downgrade(),
        next_status_id: 1,
        fetch: None,
        last_fetch: None,
    };
    tokio::spawn(registry.run(receiver));
    RegistryHandle { sender }
}

struct Registry {
    nodes: HashMap<NodeId, Node>,
    status_settings: StatusSettings,
    timer_sender: mpsc::WeakSender<RegistryRequest>,
    next_status_id: u64,
    fetch: Option<Fetch>,
    last_fetch: Option<FinishedFetch>,
}

impl Registry {
    async fn run(mut self, mut receiver: mpsc::Receiver<RegistryRequest>) {
        while let Some(request) = receiver.recv().await {
            self.handle(request).await;
        }
    }

    async fn handle(&mut self, request: RegistryRequest) {
        match request {
            RegistryRequest::Register { node_id, writer, response } => {
                self.nodes.insert(node_id, Node { writer, state: NodeState::Spawned, snapshot: None });
                let _ = response.send(());
            }
            RegistryRequest::MarkReady { node_id, response } => {
                let transitioned = match self.nodes.get_mut(&node_id) {
                    Some(node) if node.state == NodeState::Spawned => {
                        node.state = NodeState::Ready;
                        true
                    }
                    _ => false,
                };
                let _ = response.send(transitioned);
            }
            RegistryRequest::Deregister { node_id, response } => {
                let removed = self.nodes.remove(&node_id).is_some();
                if let Some(fetch) = &mut self.fetch {
                    fetch.outstanding.remove(&node_id);
                    fetch.replied.remove(&node_id);
                    if fetch.outstanding.is_empty() {
                        self.finish_fetch();
                    }
                }
                let _ = response.send(removed);
            }
            RegistryRequest::Route { line, response } => {
                let delivery = match self.nodes.get_mut(&line.target) {
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
                for (node_id, node) in self.nodes.iter_mut() {
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
            RegistryRequest::ReadyNodes { response } => {
                let mut ready: Vec<NodeId> = self.nodes.iter()
                    .filter(|(_, node)| node.state == NodeState::Ready)
                    .map(|(node_id, _)| node_id.clone())
                    .collect();
                ready.sort();
                let _ = response.send(ready);
            }
            RegistryRequest::FetchStatus { response } => {
                self.fetch_status(response).await;
            }
            RegistryRequest::StatusReply { node_id, id, snapshot, response } => {
                let _ = response.send(self.record_reply(node_id, id, snapshot));
            }
            RegistryRequest::FetchTimedOut { id } => {
                if self.fetch.as_ref().is_some_and(|fetch| fetch.id == id) {
                    self.finish_fetch();
                }
            }
        }
    }

    async fn fetch_status(&mut self, response: oneshot::Sender<StatusView>) {
        if let Some(fetch) = &mut self.fetch {
            fetch.waiters.push(response);
            return;
        }

        if let Some(last) = &self.last_fetch
            && last.finished_at.elapsed() < self.status_settings.debounce
        {
            let _ = response.send(self.view(&last.replied));
            return;
        }

        let id = self.next_status_id;
        self.next_status_id += 1;

        let mut outstanding = HashSet::new();
        for (node_id, node) in self.nodes.iter_mut() {
            if node.state != NodeState::Ready {
                continue;
            }
            let line = Line {
                source: ROUTER_ADDRESS.to_string(),
                target: node_id.clone(),
                body: Body::Control(Control::StatusRequest { id }),
            };
            if write_line(&mut node.writer, &line).await {
                outstanding.insert(node_id.clone());
            } else {
                eprintln!("Failed to send status_request {} to {}", id, node_id);
            }
        }

        let no_replies_expected = outstanding.is_empty();
        self.fetch = Some(Fetch { id, outstanding, replied: HashSet::new(), waiters: vec![response] });

        if no_replies_expected {
            self.finish_fetch();
            return;
        }

        let timer_sender = self.timer_sender.clone();
        let timeout = self.status_settings.timeout;
        tokio::spawn(async move {
            tokio::time::sleep(timeout).await;
            if let Some(sender) = timer_sender.upgrade() {
                let _ = sender.send(RegistryRequest::FetchTimedOut { id }).await;
            }
        });
    }

    fn record_reply(&mut self, node_id: NodeId, id: u64, snapshot: Value) -> bool {
        let Some(fetch) = &mut self.fetch else {
            return false;
        };
        if fetch.id != id || !fetch.outstanding.remove(&node_id) {
            return false;
        }

        if let Some(node) = self.nodes.get_mut(&node_id) {
            node.snapshot = Some(Snapshot { value: snapshot, fetched_at: Instant::now() });
        }
        fetch.replied.insert(node_id);

        if fetch.outstanding.is_empty() {
            self.finish_fetch();
        }
        true
    }

    fn finish_fetch(&mut self) {
        let Some(fetch) = self.fetch.take() else {
            return;
        };
        let view = self.view(&fetch.replied);
        for waiter in fetch.waiters {
            let _ = waiter.send(view.clone());
        }
        self.last_fetch = Some(FinishedFetch { finished_at: Instant::now(), replied: fetch.replied });
    }

    fn view(&self, replied: &HashSet<NodeId>) -> StatusView {
        self.nodes
            .iter()
            .map(|(node_id, node)| {
                let status = NodeStatus {
                    state: node.state,
                    replied: replied.contains(node_id),
                    snapshot: node.snapshot.clone(),
                };
                (node_id.clone(), status)
            })
            .collect()
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
    use crate::message::BROADCAST_ADDRESS;
    use serde_json::json;
    use tokio::io::{AsyncBufReadExt, BufReader, DuplexStream, Lines};

    const SETTINGS: StatusSettings = StatusSettings {
        timeout: Duration::from_secs(2),
        debounce: Duration::from_secs(1),
    };

    async fn register(registry: &RegistryHandle, id: &str) -> Lines<BufReader<DuplexStream>> {
        let (writer, reader) = tokio::io::duplex(4096);
        registry.register(id.to_string(), Box::new(writer)).await.unwrap();
        BufReader::new(reader).lines()
    }

    async fn register_ready(registry: &RegistryHandle, id: &str) -> Lines<BufReader<DuplexStream>> {
        let lines = register(registry, id).await;
        assert!(registry.mark_ready(id.to_string()).await.unwrap());
        lines
    }

    /// Reads the next line from a node's stdin and returns its `status_request` id.
    async fn next_status_request(lines: &mut Lines<BufReader<DuplexStream>>) -> u64 {
        let line: Line = serde_json::from_str(&lines.next_line().await.unwrap().unwrap()).unwrap();
        assert_eq!(line.source, ROUTER_ADDRESS);
        match line.body {
            Body::Control(Control::StatusRequest { id }) => id,
            body => panic!("expected status_request, got {:?}", body),
        }
    }

    fn spawn_fetch(registry: &RegistryHandle) -> tokio::task::JoinHandle<StatusView> {
        let registry = registry.clone();
        tokio::spawn(async move { registry.fetch_status().await.unwrap() })
    }

    async fn assert_no_line(lines: &mut Lines<BufReader<DuplexStream>>) {
        let next = tokio::time::timeout(Duration::from_millis(10), lines.next_line()).await;
        assert!(next.is_err(), "expected no line, got {:?}", next);
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
        let registry = start(SETTINGS);
        let mut node_2 = register(&registry, "node-2").await;

        let line = packet("node-1", "node-2");
        assert_eq!(registry.route(line.clone()).await.unwrap(), Delivery::Delivered);

        let received: Line = serde_json::from_str(&node_2.next_line().await.unwrap().unwrap()).unwrap();
        assert_eq!(received, line);
    }

    #[tokio::test]
    async fn reports_unknown_target() {
        let registry = start(SETTINGS);
        assert_eq!(registry.route(packet("node-1", "node-9")).await.unwrap(), Delivery::UnknownTarget);
    }

    #[tokio::test]
    async fn broadcasts_to_ready_nodes_except_sender() {
        let registry = start(SETTINGS);
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
    async fn lists_only_ready_nodes() {
        let registry = start(SETTINGS);
        let _node_2 = register_ready(&registry, "node-2").await;
        let _node_1 = register_ready(&registry, "node-1").await;
        let _spawned = register(&registry, "node-3").await;

        assert_eq!(registry.ready_nodes().await.unwrap(), vec!["node-1", "node-2"]);
    }

    #[tokio::test]
    async fn mark_ready_only_transitions_once() {
        let registry = start(SETTINGS);
        let _node = register(&registry, "node-1").await;

        assert!(registry.mark_ready("node-1".to_string()).await.unwrap());
        assert!(!registry.mark_ready("node-1".to_string()).await.unwrap());
        assert!(!registry.mark_ready("node-9".to_string()).await.unwrap());
    }

    #[tokio::test]
    async fn deregistered_nodes_are_unknown() {
        let registry = start(SETTINGS);
        let _node = register(&registry, "node-2").await;

        assert!(registry.deregister("node-2".to_string()).await.unwrap());
        assert_eq!(registry.route(packet("node-1", "node-2")).await.unwrap(), Delivery::UnknownTarget);
    }

    #[tokio::test(start_paused = true)]
    async fn fetch_returns_once_every_ready_node_replies() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;
        let mut node_2 = register_ready(&registry, "node-2").await;
        let mut spawned = register(&registry, "node-3").await;

        let fetch = spawn_fetch(&registry);
        let id = next_status_request(&mut node_1).await;
        assert_eq!(next_status_request(&mut node_2).await, id);

        assert!(registry.status_reply("node-1".to_string(), id, json!({"incarnation": 1})).await.unwrap());
        assert!(registry.status_reply("node-2".to_string(), id, json!({"incarnation": 2})).await.unwrap());

        let view = fetch.await.unwrap();
        assert!(view["node-1"].replied);
        assert_eq!(view["node-1"].snapshot.as_ref().unwrap().value, json!({"incarnation": 1}));
        assert_eq!(view["node-2"].snapshot.as_ref().unwrap().value, json!({"incarnation": 2}));
        assert_eq!(view["node-3"].state, NodeState::Spawned);
        assert!(!view["node-3"].replied);
        assert_no_line(&mut spawned).await;
    }

    #[tokio::test(start_paused = true)]
    async fn silent_node_keeps_its_previous_snapshot_after_timeout() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;

        let fetch = spawn_fetch(&registry);
        let id = next_status_request(&mut node_1).await;
        registry.status_reply("node-1".to_string(), id, json!({"incarnation": 1})).await.unwrap();
        let first = fetch.await.unwrap();

        tokio::time::advance(SETTINGS.debounce).await;
        let started = Instant::now();
        let fetch = spawn_fetch(&registry);
        next_status_request(&mut node_1).await;
        let second = fetch.await.unwrap();

        assert!(started.elapsed() >= SETTINGS.timeout);
        assert!(!second["node-1"].replied);
        assert_eq!(second["node-1"].snapshot, first["node-1"].snapshot);
    }

    #[tokio::test(start_paused = true)]
    async fn fetch_within_debounce_is_answered_from_cache() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;

        let fetch = spawn_fetch(&registry);
        let id = next_status_request(&mut node_1).await;
        registry.status_reply("node-1".to_string(), id, json!({})).await.unwrap();
        let first = fetch.await.unwrap();

        let cached = registry.fetch_status().await.unwrap();
        assert_eq!(cached, first);
        assert_no_line(&mut node_1).await;
    }

    #[tokio::test(start_paused = true)]
    async fn concurrent_fetches_share_one_request() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;

        let first = spawn_fetch(&registry);
        let id = next_status_request(&mut node_1).await;
        let second = spawn_fetch(&registry);
        tokio::task::yield_now().await;
        assert_no_line(&mut node_1).await;

        registry.status_reply("node-1".to_string(), id, json!({})).await.unwrap();
        assert_eq!(first.await.unwrap(), second.await.unwrap());
    }

    #[tokio::test(start_paused = true)]
    async fn unmatched_replies_are_rejected() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;

        assert!(!registry.status_reply("node-1".to_string(), 1, json!({})).await.unwrap());

        let fetch = spawn_fetch(&registry);
        let id = next_status_request(&mut node_1).await;
        assert!(!registry.status_reply("node-1".to_string(), id + 1, json!({})).await.unwrap());
        assert!(!registry.status_reply("node-9".to_string(), id, json!({})).await.unwrap());
        assert!(registry.status_reply("node-1".to_string(), id, json!({})).await.unwrap());
        assert!(!registry.status_reply("node-1".to_string(), id, json!({})).await.unwrap());
        fetch.await.unwrap();
    }

    #[tokio::test(start_paused = true)]
    async fn deregistering_the_last_outstanding_node_finishes_the_fetch() {
        let registry = start(SETTINGS);
        let mut node_1 = register_ready(&registry, "node-1").await;

        let started = Instant::now();
        let fetch = spawn_fetch(&registry);
        next_status_request(&mut node_1).await;
        registry.deregister("node-1".to_string()).await.unwrap();

        assert!(fetch.await.unwrap().is_empty());
        assert!(started.elapsed() < SETTINGS.timeout);
    }

    #[tokio::test(start_paused = true)]
    async fn fetch_with_no_ready_nodes_finishes_immediately() {
        let registry = start(SETTINGS);
        let _spawned = register(&registry, "node-1").await;

        let view = registry.fetch_status().await.unwrap();
        assert!(!view["node-1"].replied);
        assert!(view["node-1"].snapshot.is_none());
    }
}
