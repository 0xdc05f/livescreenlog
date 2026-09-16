# LiveScreenLog API

Base URL: server origin (e.g. `http://localhost:8080`)

## Ingest

### `POST /api/sessions`
Create a session (or return policy-disabled response). **permitAll** (no auth); `projectKey` is validated in `SessionIngestionService`.

**Body**
```json
{
  "projectKey": "string",
  "userId": "string",
  "distinctId": "string",
  "source": "string",
  "trigger": "FORCE | ERROR | null"
}
```

**Response**
```json
{
  "sessionId": "uuid|null",
  "token": "hmac-token|null",
  "enabled": true,
  "recordingMode": "ALL|A|B|C|NONE"
}
```

Rate limited by IP + projectKey (`livescreenlog.rate-limit.session-create-per-minute`).

### `POST /api/events`
Append rrweb event batch. Requires header `x-livescreenlog-session-token`.

**Body**: JSON array of rrweb events.

### `POST /api/heartbeat` / `POST /api/stop`
Session keep-alive / stop. Same HMAC header.

## Read (dashboard)

Requires admin login (form / session). API paths use hasAnyRole("SUPER_ADMIN", "ADMIN"). Network isolation still recommended in prod. HMAC for ingest only.

### `GET /api/sessions`
Query params: `startDate`, `endDate`, `userId`, `source`, `status`, `projectKey`, `query`, `page`, `size`, `sort`, `updatedAfter`.

### `GET /api/sessions/{id}`
Session metadata.

### `GET /api/sessions/{id}/events`
- Default (no params): full event list (legacy).
- Paged: `?paged=true&limit=2000&afterId=123`

**Paged response**
```json
{
  "events": [{"id": 1, "sessionId": "...", "timestamp": 0, "eventData": {}}],
  "nextAfterId": 1,
  "hasMore": false
}
```

### `GET /api/sessions/live`
List SSE for active sessions (event name `session_created`).

### `GET /api/sessions/recommended?limit=`
Error-triggered recommendations (dashboard).

### `GET /api/sessions/{id}/live`
Per-session SSE live tail (`text/event-stream`). Events named `message` (JSON array payload). Comment pings every 15s. Dashboard fetches events with `paged=true` by default.

### `GET /api/stats/overview?projectKey=`
Usage stats (hours/days/months + live counts). Requires ADMIN.

### `GET /api/me`
Current authenticated user.

### `POST /api/me/password`
Change password (body: currentPassword, newPassword).

## Push signaling (Mode B)

### `GET /api/push/connect?projectKey=&userId=`
SDK standby SSE. Validates project key. Events: `INIT`, `START_RECORDING`.

**Note:** Emitter registry is process-local (single instance).

### `GET /api/push/active-terminals?projectKey=`
### `POST /api/push/trigger-record?projectKey=&userId=`
Requires SUPER_ADMIN or ADMIN plus access to the project. Issues a short-lived FORCE nonce for the SDK.

## Projects

GET `/api/projects/**` — SUPER_ADMIN / ADMIN / VIEWER.
Mutations — SUPER_ADMIN / ADMIN, scoped by project assignment.

## Ops

### `GET /actuator/health`
Liveness/readiness.

## Auth headers

| Header | Use |
| :--- | :--- |
| `x-livescreenlog-session-token` | Ingest after session create (`/api/events`, `/api/heartbeat`, `/api/stop`) |

Dashboard routes use form-login cookies (CSRF on mutating calls). Isolate the dashboard at the reverse proxy in production. See `docs/security/SECURITY.md`.
