use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use serde::Deserialize;

use crate::message::RESERVED_PREFIX;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub implementations: HashMap<String, ImplementationConfig>,
    pub nodes: Vec<NodeConfig>,
}

#[derive(Debug, Deserialize)]
pub struct ImplementationConfig {
    pub executable: PathBuf,
    #[serde(default)]
    pub args: HashMap<String, String>,
}

#[derive(Debug, Deserialize)]
pub struct NodeConfig {
    pub id: String,
    pub implementation: String,
    #[serde(default)]
    pub args: HashMap<String, String>,
}

impl Config {
    pub fn parse(raw: &str) -> anyhow::Result<Config> {
        let config: Config = serde_json::from_str(raw)?;
        config.validate()?;
        Ok(config)
    }

    fn validate(&self) -> anyhow::Result<()> {
        let mut seen = HashSet::new();
        for node in &self.nodes {
            if node.id.is_empty() {
                anyhow::bail!("Node IDs may not be empty");
            }
            if node.id.starts_with(RESERVED_PREFIX) {
                anyhow::bail!("Node ID '{}' may not start with '{}'", node.id, RESERVED_PREFIX);
            }
            if !seen.insert(node.id.as_str()) {
                anyhow::bail!("Duplicate node ID '{}'", node.id);
            }
            if !self.implementations.contains_key(&node.implementation) {
                anyhow::bail!("Unknown implementation '{}' for node '{}'", node.implementation, node.id);
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config_with_ids(ids: &[&str]) -> String {
        let nodes: Vec<String> = ids
            .iter()
            .map(|id| format!(r#"{{"id":"{}","implementation":"java"}}"#, id))
            .collect();
        format!(
            r#"{{"implementations":{{"java":{{"executable":"run.sh"}}}},"nodes":[{}]}}"#,
            nodes.join(",")
        )
    }

    #[test]
    fn accepts_valid_config() {
        assert!(Config::parse(&config_with_ids(&["node-1", "node-2"])).is_ok());
    }

    #[test]
    fn rejects_reserved_prefix() {
        assert!(Config::parse(&config_with_ids(&["__all__"])).is_err());
        assert!(Config::parse(&config_with_ids(&["__node"])).is_err());
    }

    #[test]
    fn rejects_duplicate_ids() {
        assert!(Config::parse(&config_with_ids(&["node-1", "node-1"])).is_err());
    }

    #[test]
    fn rejects_unknown_implementation() {
        let raw = r#"{"implementations":{},"nodes":[{"id":"node-1","implementation":"java"}]}"#;
        assert!(Config::parse(raw).is_err());
    }
}
