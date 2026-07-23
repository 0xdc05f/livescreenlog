# LiveScreenLog API

Base URL: server origin (e.g. `http://localhost:8080`)

## Ingest

### `POST /api/sessions`
Create a session (or return policy-disabled response).

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

No app-level login. Open at the HTTP layer — protect with network isolation. HMAC still protects ingest only.

### `GET /api/sessions`
Query params: `startDate`, `endDate`, `userId`, `source`, `status`, `projectKey`, `query`, `page`, `size`, `sort`.

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

### `GET /api/sessions/{id}/live`
SSE live tail (`text/event-stream`). Events named `message` (JSON array payload). Comment pings every 15s.

## Push signaling (Mode B)

### `GET /api/push/connect?projectKey=&userId=`
SDK standby SSE. Validates project key. Events: `INIT`, `START_RECORDING`.

**Note:** Emitter registry is process-local (single instance).

### `GET /api/push/active-terminals?projectKey=`
### `POST /api/push/trigger-record?projectKey=&userId=`
Open (no login). Mode B works without dashboard credentials.

## Projects

All under `/api/projects/**` — open (no login); network-isolate in production.

## Ops

### `GET /actuator/health`
Liveness/readiness.

## Auth headers

| Header | Use |
| :--- | :--- |
| `x-livescreenlog-session-token` | Ingest after session create (`/api/events`, `/api/heartbeat`, `/api/stop`) |

Read, project, and push-admin routes are **not** protected by app-level Basic Auth. Isolate them at the network / reverse-proxy layer in production. See `docs/security/SECURITY.md`.
