use std::collections::{BTreeMap, HashSet};

use serde::{Deserialize, Serialize};

use crate::registry::NodeId;

/// One direction of the simulated network between two nodes.
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Deserialize, Serialize)]
pub struct Link {
    pub source: NodeId,
    pub target: NodeId,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct BlockedLink {
    #[serde(flatten)]
    pub link: Link,
    /// Packets dropped on this link since it was blocked.
    pub dropped: u64,
}

/// A change to the simulated network, checked against the config's node IDs.
#[derive(Debug, Clone)]
pub enum NetworkChange {
    /// Replace every block with a cut between each pair of groups, in both directions.
    /// Nodes in no group form one extra group.
    Partition { groups: Vec<Vec<NodeId>> },
    Block(Link),
    Unblock(Link),
    /// Remove every block.
    Heal,
}

/// The simulated network: which directed links drop packets. Everything not blocked
/// is delivered.
#[derive(Debug, Default)]
pub struct Network {
    /// Blocked links and how many packets each has dropped.
    blocked: BTreeMap<Link, u64>,
}

impl Network {
    /// Whether a packet from `source` to `target` is delivered. Counts it as dropped if not.
    pub fn deliver(&mut self, source: &str, target: &str) -> bool {
        let link = Link { source: source.to_string(), target: target.to_string() };
        match self.blocked.get_mut(&link) {
            Some(dropped) => {
                *dropped += 1;
                false
            }
            None => true,
        }
    }

    pub fn blocked(&self) -> Vec<BlockedLink> {
        self.blocked.iter()
            .map(|(link, dropped)| BlockedLink { link: link.clone(), dropped: *dropped })
            .collect()
    }

    /// Applies the change, or leaves the network unchanged and explains why not.
    pub fn apply(&mut self, change: NetworkChange, nodes: &[NodeId]) -> Result<(), String> {
        let known = |id: &NodeId| -> Result<(), String> {
            if nodes.contains(id) { Ok(()) } else { Err(format!("unknown node '{}'", id)) }
        };

        match change {
            NetworkChange::Partition { groups } => {
                let mut seen = HashSet::new();
                for id in groups.iter().flatten() {
                    known(id)?;
                    if !seen.insert(id) {
                        return Err(format!("node '{}' is in more than one group", id));
                    }
                }
                let rest: Vec<NodeId> = nodes.iter().filter(|id| !seen.contains(id)).cloned().collect();
                let groups: Vec<&Vec<NodeId>> = groups.iter().chain([&rest]).filter(|g| !g.is_empty()).collect();

                // Keep the counts of links that stay blocked.
                let mut blocked = BTreeMap::new();
                for (i, group) in groups.iter().enumerate() {
                    for other in groups.iter().skip(i + 1) {
                        for a in group.iter() {
                            for b in other.iter() {
                                for link in [Link { source: a.clone(), target: b.clone() }, Link { source: b.clone(), target: a.clone() }] {
                                    let dropped = self.blocked.get(&link).copied().unwrap_or(0);
                                    blocked.insert(link, dropped);
                                }
                            }
                        }
                    }
                }
                self.blocked = blocked;
            }
            NetworkChange::Block(link) => {
                known(&link.source)?;
                known(&link.target)?;
                if link.source == link.target {
                    return Err("a node cannot be blocked from itself".to_string());
                }
                self.blocked.entry(link).or_insert(0);
            }
            NetworkChange::Unblock(link) => {
                known(&link.source)?;
                known(&link.target)?;
                self.blocked.remove(&link);
            }
            NetworkChange::Heal => self.blocked.clear(),
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ids(ids: &[&str]) -> Vec<NodeId> {
        ids.iter().map(|id| id.to_string()).collect()
    }

    fn link(source: &str, target: &str) -> Link {
        Link { source: source.to_string(), target: target.to_string() }
    }

    fn links(network: &Network) -> Vec<Link> {
        network.blocked().into_iter().map(|b| b.link).collect()
    }

    const NODES: &[&str] = &["node-1", "node-2", "node-3"];

    #[test]
    fn partition_cuts_between_groups_in_both_directions() {
        let mut network = Network::default();
        let groups = vec![ids(&["node-1"]), ids(&["node-2", "node-3"])];
        network.apply(NetworkChange::Partition { groups }, &ids(NODES)).unwrap();

        assert_eq!(links(&network), vec![
            link("node-1", "node-2"), link("node-1", "node-3"),
            link("node-2", "node-1"), link("node-3", "node-1"),
        ]);
        assert!(!network.deliver("node-2", "node-1"));
        assert!(network.deliver("node-2", "node-3"));
    }

    #[test]
    fn unlisted_nodes_form_their_own_group() {
        let mut network = Network::default();
        let groups = vec![ids(&["node-1"])];
        network.apply(NetworkChange::Partition { groups }, &ids(NODES)).unwrap();

        assert!(!network.deliver("node-1", "node-2"));
        assert!(!network.deliver("node-3", "node-1"));
        assert!(network.deliver("node-2", "node-3"));
    }

    #[test]
    fn partition_rejects_unknown_and_repeated_nodes() {
        let mut network = Network::default();
        let unknown = vec![ids(&["node-9"])];
        assert!(network.apply(NetworkChange::Partition { groups: unknown }, &ids(NODES)).is_err());
        let repeated = vec![ids(&["node-1"]), ids(&["node-1", "node-2"])];
        assert!(network.apply(NetworkChange::Partition { groups: repeated }, &ids(NODES)).is_err());
        assert!(network.blocked().is_empty());
    }

    #[test]
    fn block_is_one_way_and_counts_drops() {
        let mut network = Network::default();
        network.apply(NetworkChange::Block(link("node-1", "node-2")), &ids(NODES)).unwrap();

        assert!(!network.deliver("node-1", "node-2"));
        assert!(!network.deliver("node-1", "node-2"));
        assert!(network.deliver("node-2", "node-1"));
        assert_eq!(network.blocked(), vec![BlockedLink { link: link("node-1", "node-2"), dropped: 2 }]);

        network.apply(NetworkChange::Unblock(link("node-1", "node-2")), &ids(NODES)).unwrap();
        assert!(network.deliver("node-1", "node-2"));
    }

    #[test]
    fn block_rejects_self_and_unknown_links() {
        let mut network = Network::default();
        assert!(network.apply(NetworkChange::Block(link("node-1", "node-1")), &ids(NODES)).is_err());
        assert!(network.apply(NetworkChange::Block(link("node-1", "node-9")), &ids(NODES)).is_err());
    }

    #[test]
    fn heal_removes_every_block() {
        let mut network = Network::default();
        network.apply(NetworkChange::Partition { groups: vec![ids(&["node-1"])] }, &ids(NODES)).unwrap();
        network.apply(NetworkChange::Heal, &ids(NODES)).unwrap();
        assert!(network.blocked().is_empty());
    }
}
