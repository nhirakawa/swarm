use serde::{Deserialize, Serialize};

/// The router's own address: the target of node → router control lines and
/// the source of router → node ones.
pub const ROUTER_ADDRESS: &str = "__router__";

/// Broadcast address: a packet sent here is copied to every Ready node except the sender.
pub const BROADCAST_ADDRESS: &str = "__all__";

/// Prefix reserved for router addresses; config IDs may not use it.
pub const RESERVED_PREFIX: &str = "__";

/// One newline-delimited JSON line exchanged over a node's stdin/stdout.
#[derive(Debug, Clone, PartialEq, Deserialize, Serialize)]
#[serde(try_from = "RawLine", into = "RawLine")]
pub struct Line {
    pub source: String,
    pub target: String,
    pub body: Body,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Body {
    /// Base64 of a framed protocol message. Forwarded without decoding.
    Packet(String),
    Control(Control),
}

#[derive(Debug, Clone, PartialEq, Deserialize, Serialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum Control {
    Ready,
    StatusRequest { id: u64 },
    Status { id: u64, snapshot: serde_json::Value },
    /// Any kind this router does not know. Receivers ignore it.
    #[serde(other)]
    Unknown,
}

/// Wire shape of a line, so that "exactly one of `packet` or `control`" can be enforced.
#[derive(Deserialize, Serialize)]
struct RawLine {
    source: String,
    target: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    packet: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    control: Option<Control>,
}

impl TryFrom<RawLine> for Line {
    type Error = &'static str;

    fn try_from(raw: RawLine) -> Result<Self, Self::Error> {
        let body = match (raw.packet, raw.control) {
            (Some(packet), None) => Body::Packet(packet),
            (None, Some(control)) => Body::Control(control),
            (Some(_), Some(_)) => return Err("line has both packet and control"),
            (None, None) => return Err("line has neither packet nor control"),
        };
        Ok(Line { source: raw.source, target: raw.target, body })
    }
}

impl From<Line> for RawLine {
    fn from(line: Line) -> Self {
        let (packet, control) = match line.body {
            Body::Packet(packet) => (Some(packet), None),
            Body::Control(control) => (None, Some(control)),
        };
        RawLine { source: line.source, target: line.target, packet, control }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(json: &str) -> Result<Line, serde_json::Error> {
        serde_json::from_str(json)
    }

    #[test]
    fn parses_packet() {
        let line = parse(r#"{"source":"node-1","target":"node-2","packet":"AAIA"}"#).unwrap();
        assert_eq!(line.body, Body::Packet("AAIA".to_string()));
    }

    #[test]
    fn parses_controls() {
        let ready = parse(r#"{"source":"node-1","target":"__router__","control":{"kind":"ready"}}"#).unwrap();
        assert_eq!(ready.body, Body::Control(Control::Ready));

        let status = parse(
            r#"{"source":"node-1","target":"__router__","control":{"kind":"status","id":7,"snapshot":{"incarnation":3}}}"#,
        )
        .unwrap();
        assert_eq!(
            status.body,
            Body::Control(Control::Status { id: 7, snapshot: serde_json::json!({"incarnation": 3}) })
        );
    }

    #[test]
    fn unknown_kind_is_tolerated() {
        let line = parse(r#"{"source":"node-1","target":"__router__","control":{"kind":"later","x":1}}"#).unwrap();
        assert_eq!(line.body, Body::Control(Control::Unknown));
    }

    #[test]
    fn rejects_both_or_neither_body() {
        assert!(parse(r#"{"source":"a","target":"b","packet":"AA","control":{"kind":"ready"}}"#).is_err());
        assert!(parse(r#"{"source":"a","target":"b"}"#).is_err());
    }

    #[test]
    fn serializes_only_the_present_body() {
        let line = Line {
            source: ROUTER_ADDRESS.to_string(),
            target: "node-1".to_string(),
            body: Body::Control(Control::StatusRequest { id: 1 }),
        };
        assert_eq!(
            serde_json::to_string(&line).unwrap(),
            r#"{"source":"__router__","target":"node-1","control":{"kind":"status_request","id":1}}"#
        );
    }
}
