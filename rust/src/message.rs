use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
pub struct Message {
    pub source: String,
    pub target: String,
    pub payload: String,
}
