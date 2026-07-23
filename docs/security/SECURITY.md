# LiveScreenLog Security Guidelines

## 1. Authentication & Authorization

### 1.1 Ingest API
- **Targets**: `POST /api/sessions`, `POST /api/events`, `POST /api/heartbeat`, `POST /api/stop`
- **Session create**: body `projectKey` must match a registered project API key or `LIVESCREENLOG_PROJECT_KEY`.
- **Subsequent calls**: header `x-livescreenlog-session-token` (HMAC-SHA256, expiry). Verified by `HmacAuthenticationFilter` → `ROLE_SESSION`.
- **Rate limits** (Redis): create per IP+projectKey, append per sessionId (`livescreenlog.rate-limit.*`).

### 1.2 Read / Project / Push admin API
- **No dashboard login.** HTTP Basic and `ROLE_DASHBOARD_ADMIN` are not used.
- Targets are open at the app layer: `GET /api/sessions/**`, `GET /api/projects/**`, `/api/push/**` (including trigger-record / active-terminals).
- **Protect with network isolation** (private network, reverse-proxy auth, VPN, or firewall). Do not expose the dashboard/read APIs publicly without an external auth layer.
- HMAC still protects ingest only (`/api/events`, `/api/heartbeat`, `/api/stop`).

### 1.3 Push connect (SDK)
- `GET /api/push/connect` is public but requires a valid `projectKey`.
- Mode B push admin paths work without login so operators can trigger recording without dashboard credentials.

### 1.4 Production fail-fast
`prod` profile refuses to start if:
- weak/short `LIVESCREENLOG_HMAC_SECRET`
- `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` missing or contains `*`

## 2. CORS

- `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` comma-separated allow-list.
- Production must not use `*`.

## 3. Data Masking

SDK defaults:
- `maskAllInputs: true`
- `blockClass: livescreenlog-block`
- `ignoreClass: livescreenlog-ignore`

## 4. Retention

- `LIVESCREENLOG_RETENTION_DAYS` (default 30). Daily job deletes old `session_metadata` (CASCADE events). Set `0` to disable.
