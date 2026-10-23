use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Response};
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::Serialize;
use serde_json::{Value, json};

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
        let request = Request::builder().method(method).uri(uri).body(Body::empty()).unwrap();
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
