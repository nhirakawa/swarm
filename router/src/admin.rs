use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Response};
use axum::routing::{get, post, put};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use crate::network::{BlockedLink, Link, NetworkChange};
use crate::nodes::{ActionError, Exit, Supervisor};
use crate::registry::{NodeState, RegistryHandle};

/// The admin UI: one self-contained page that calls the API below.
const ADMIN_PAGE: &str = include_str!("../assets/admin.html");

#[derive(Clone)]
struct AdminState {
    supervisor: Supervisor,
    registry: RegistryHandle,
}

pub fn router(supervisor: Supervisor, registry: RegistryHandle) -> Router {
    Router::new()
        .route("/", get(|| async { Html(ADMIN_PAGE) }))
        .route("/nodes", get(list_nodes))
        .route("/nodes/{id}/restart", post(restart))
        .route("/nodes/{id}/kill", post(kill))
        .route("/nodes/{id}/stop", post(stop))
        .route("/network", get(get_network).delete(heal))
        .route("/network/partition", put(partition))
        .route("/network/links/{source}/{target}", put(block).delete(unblock))
        .with_state(AdminState { supervisor, registry })
}

#[derive(Debug, Serialize)]
struct NodesResponse {
    nodes: Vec<NodeView>,
}

#[derive(Debug, Serialize)]
struct NodeView {
    id: String,
    state: &'static str,
    spawns: u32,
    /// The last process's exit, present only while the node is Exited.
    exit: Option<Exit>,
    /// The node's status from this fetch, present only while a process is running.
    status: Option<StatusView>,
}

#[derive(Debug, Serialize)]
struct StatusView {
    replied: bool,
    snapshot: Option<Value>,
    snapshot_age_ms: Option<u128>,
}

/// Fetches status from every Ready node and lists every config node.
async fn list_nodes(State(state): State<AdminState>) -> Response {
    let statuses = match state.registry.fetch_status().await {
        Ok(statuses) => statuses,
        Err(e) => return error(StatusCode::INTERNAL_SERVER_ERROR, &e.to_string()),
    };

    let nodes = state.supervisor.records().into_iter()
        .map(|record| {
            let live = statuses.get(&record.id);
            let node_state = match (&record.exit, live.map(|status| status.state)) {
                (Some(_), _) => "exited",
                (None, Some(NodeState::Ready)) => "ready",
                // A process that has not registered yet is still starting.
                (None, _) => "spawned",
            };
            let status = match (&record.exit, live) {
                (None, Some(live)) => Some(StatusView {
                    replied: live.replied,
                    snapshot: live.snapshot.as_ref().map(|snapshot| snapshot.value.clone()),
                    snapshot_age_ms: live.snapshot.as_ref().map(|snapshot| snapshot.fetched_at.elapsed().as_millis()),
                }),
                _ => None,
            };
            NodeView { id: record.id, state: node_state, spawns: record.spawns, exit: record.exit, status }
        })
        .collect();

    Json(NodesResponse { nodes }).into_response()
}

async fn restart(State(state): State<AdminState>, Path(id): Path<String>) -> Response {
    action_response(state.supervisor.restart(&id).await)
}

async fn kill(State(state): State<AdminState>, Path(id): Path<String>) -> Response {
    action_response(state.supervisor.kill(&id).await)
}

async fn stop(State(state): State<AdminState>, Path(id): Path<String>) -> Response {
    action_response(state.supervisor.stop(&id).await)
}

#[derive(Debug, Serialize)]
struct NetworkResponse {
    blocked: Vec<BlockedLink>,
}

#[derive(Debug, Deserialize)]
struct PartitionRequest {
    groups: Vec<Vec<String>>,
}

async fn get_network(State(state): State<AdminState>) -> Response {
    match state.registry.network().await {
        Ok(blocked) => Json(NetworkResponse { blocked }).into_response(),
        Err(e) => error(StatusCode::INTERNAL_SERVER_ERROR, &e.to_string()),
    }
}

async fn partition(State(state): State<AdminState>, Json(request): Json<PartitionRequest>) -> Response {
    change_network(&state, NetworkChange::Partition { groups: request.groups }).await
}

async fn block(State(state): State<AdminState>, Path((source, target)): Path<(String, String)>) -> Response {
    change_network(&state, NetworkChange::Block(Link { source, target })).await
}

async fn unblock(State(state): State<AdminState>, Path((source, target)): Path<(String, String)>) -> Response {
    change_network(&state, NetworkChange::Unblock(Link { source, target })).await
}

async fn heal(State(state): State<AdminState>) -> Response {
    change_network(&state, NetworkChange::Heal).await
}

async fn change_network(state: &AdminState, change: NetworkChange) -> Response {
    match state.registry.change_network(change, state.supervisor.node_ids()).await {
        Ok(Ok(blocked)) => Json(NetworkResponse { blocked }).into_response(),
        Ok(Err(reason)) => error(StatusCode::BAD_REQUEST, &reason),
        Err(e) => error(StatusCode::INTERNAL_SERVER_ERROR, &e.to_string()),
    }
}

/// Actions only start the change; `GET /nodes` shows when it has happened.
fn action_response(result: Result<(), ActionError>) -> Response {
    match result {
        Ok(()) => StatusCode::ACCEPTED.into_response(),
        Err(ActionError::UnknownNode) => error(StatusCode::NOT_FOUND, "unknown node"),
        Err(ActionError::NotRunning) => error(StatusCode::CONFLICT, "node has already exited"),
        Err(ActionError::NotExited) => error(StatusCode::CONFLICT, "node is still running"),
        Err(ActionError::Spawn(e)) => error(StatusCode::INTERNAL_SERVER_ERROR, &format!("failed to spawn: {}", e)),
    }
}

fn error(status: StatusCode, message: &str) -> Response {
    (status, Json(json!({ "error": message }))).into_response()
}

#[cfg(test)]
mod tests {
    use axum::body::Body;
    use axum::http::Request;
    use tower::ServiceExt;

    use super::*;
    use crate::nodes::test_support::start;

    async fn call(app: &Router, method: &str, uri: &str) -> (StatusCode, Value) {
        call_with(app, method, uri, None).await
    }

    async fn call_with(app: &Router, method: &str, uri: &str, body: Option<Value>) -> (StatusCode, Value) {
        let request = Request::builder().method(method).uri(uri);
        let request = match body {
            Some(body) => request.header("content-type", "application/json").body(Body::from(body.to_string())),
            None => request.body(Body::empty()),
        }
        .unwrap();
        let response = app.clone().oneshot(request).await.unwrap();
        let status = response.status();
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX).await.unwrap();
        let body = if bytes.is_empty() { Value::Null } else { serde_json::from_slice(&bytes).unwrap() };
        (status, body)
    }

    #[tokio::test]
    async fn lists_nodes_with_status_and_exits() {
        let harness = start(&[("node-1", &[]), ("node-2", &[])]).await;
        harness.ready("node-1").await;
        harness.ready("node-2").await;
        let app = router(harness.supervisor.clone(), harness.registry.clone());

        assert_eq!(call(&app, "POST", "/nodes/node-2/kill").await.0, StatusCode::ACCEPTED);
        harness.exited("node-2").await;

        let (status, body) = call(&app, "GET", "/nodes").await;
        assert_eq!(status, StatusCode::OK);
        let nodes = body["nodes"].as_array().unwrap();
        assert_eq!(nodes[0]["id"], "node-1");
        assert_eq!(nodes[0]["state"], "ready");
        assert_eq!(nodes[0]["spawns"], 1);
        assert_eq!(nodes[0]["exit"], Value::Null);
        assert_eq!(nodes[0]["status"]["replied"], true);
        assert_eq!(nodes[0]["status"]["snapshot"], json!({"peers": "node-2"}));
        assert_eq!(nodes[1]["state"], "exited");
        assert_eq!(nodes[1]["exit"], json!({"code": null, "signal": 9, "reason": "killed"}));
        assert_eq!(nodes[1]["status"], Value::Null);
    }

    #[tokio::test]
    async fn partitions_blocks_and_heals_the_network() {
        let harness = start(&[("node-1", &[]), ("node-2", &[]), ("node-3", &[])]).await;
        let app = router(harness.supervisor.clone(), harness.registry.clone());

        let groups = json!({"groups": [["node-1"]]});
        let (status, body) = call_with(&app, "PUT", "/network/partition", Some(groups)).await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(body["blocked"].as_array().unwrap().len(), 4);
        assert_eq!(body["blocked"][0], json!({"source": "node-1", "target": "node-2", "dropped": 0}));

        let (status, body) = call(&app, "DELETE", "/network").await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(body, json!({"blocked": []}));

        assert_eq!(call(&app, "PUT", "/network/links/node-2/node-3").await.0, StatusCode::OK);
        let (_, body) = call(&app, "GET", "/network").await;
        assert_eq!(body, json!({"blocked": [{"source": "node-2", "target": "node-3", "dropped": 0}]}));
        let (_, body) = call(&app, "DELETE", "/network/links/node-2/node-3").await;
        assert_eq!(body, json!({"blocked": []}));

        let (status, body) = call(&app, "PUT", "/network/links/node-2/node-9").await;
        assert_eq!(status, StatusCode::BAD_REQUEST);
        assert_eq!(body, json!({"error": "unknown node 'node-9'"}));
    }

    #[tokio::test]
    async fn serves_the_admin_page() {
        let harness = start(&[]).await;
        let app = router(harness.supervisor.clone(), harness.registry.clone());

        let request = Request::builder().uri("/").body(Body::empty()).unwrap();
        let response = app.oneshot(request).await.unwrap();
        assert_eq!(response.status(), StatusCode::OK);
        assert!(response.headers()["content-type"].to_str().unwrap().starts_with("text/html"));
    }

    #[tokio::test]
    async fn actions_map_errors_to_status_codes() {
        let harness = start(&[("node-1", &[])]).await;
        harness.ready("node-1").await;
        let app = router(harness.supervisor.clone(), harness.registry.clone());

        assert_eq!(call(&app, "POST", "/nodes/node-9/kill").await.0, StatusCode::NOT_FOUND);
        let (status, body) = call(&app, "POST", "/nodes/node-1/restart").await;
        assert_eq!(status, StatusCode::CONFLICT);
        assert_eq!(body, json!({"error": "node is still running"}));

        call(&app, "POST", "/nodes/node-1/stop").await;
        harness.exited("node-1").await;
        assert_eq!(call(&app, "POST", "/nodes/node-1/stop").await.0, StatusCode::CONFLICT);
        assert_eq!(call(&app, "POST", "/nodes/node-1/restart").await.0, StatusCode::ACCEPTED);
    }
}
