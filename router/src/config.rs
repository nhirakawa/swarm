use std::collections::HashMap;
use std::path::PathBuf;

use serde::Deserialize;

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
    pub admin_port: u16,
    #[serde(default)]
    pub args: HashMap<String, String>,
}
