use std::collections::HashMap;
use std::path::PathBuf;

use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub implementations: HashMap<String, PathBuf>,
    pub nodes: Vec<NodeConfig>,
}

#[derive(Debug, Deserialize)]
pub struct NodeConfig {
    pub id: String,
    pub implementation: String,
}
