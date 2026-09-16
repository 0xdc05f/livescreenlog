# LiveScreenLog Security Guidelines

## 1. Authentication & Authorization

### 1.1 Ingest API
- **Targets**: `POST /api/sessions`, `POST /api/events`, `POST /api/heartbeat`, `POST /api/stop`
- **Session create**: body `projectKey` must match a registered project API key or `LIVESCREENLOG_PROJECT_KEY`.
- **Subsequent calls**: header `x-livescreenlog-session-token` (HMAC-SHA256, expiry). Verified by `HmacAuthenticationFilter` → `ROLE_SESSION`.
- **Rate limits** (Redis): create per IP+projectKey, append per sessionId (`livescreenlog.rate-limit.*`).

### 1.2 Read / Project / Push admin API
- Dashboard uses form login (`/login`) with DB users: SUPER_ADMIN, ADMIN, VIEWER.
- GET `/api/sessions/**`, GET `/api/projects/**`, GET `/api/stats/**`, and `/` allow SUPER_ADMIN / ADMIN / VIEWER.
- Project mutations and session stop/delete require SUPER_ADMIN / ADMIN plus project ACL.
- POST /api/sessions is permitAll (projectKey validated in SessionIngestionService).
- Ingest: HMAC for `/api/events`, `/api/heartbeat`, `/api/stop` (ROLE_SESSION). Stopped sessions cannot append.
- `GET /api/push/connect` is public (valid projectKey). `active-terminals` / `trigger-record` require ADMIN and project ACL.
- Still recommend network isolation or reverse proxy for production exposure of dashboard.
- `requestCache` is disabled; authenticated GET /login forwards to dashboard. `login.html` does a fetch to `/api/me` to decide.
- HMAC filter restores the previous SecurityContext (does not overwrite dashboard JSESSIONID).

### 1.3 Push connect (SDK)
- `GET /api/push/connect` is public but requires a valid `projectKey`.
- Mode B recording is started from the dashboard (`trigger-record`). The SDK `FORCE` trigger is accepted only after that authenticated call. Triggers are fanned out over Redis so multiple app instances can signal standby clients.

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

## 5. Initial Super Admin Bootstrap (Phase 6)

For the first-time setup of dashboard/admin accounts:

- On startup, if the `users` table is empty, the app can auto-create one **SUPER_ADMIN** account.
- Configure via environment or command line:
  ```bash
  --livescreenlog.security.dashboard-username=superadmin
  --livescreenlog.security.dashboard-password='YourStrongPasswordHere123!'
  ```
- The password is BCrypt-hashed on creation.
- This is the supported way for "super user admin first creation from server".
- After creation, change password via Settings → account (AccountView) or `POST /api/me/password`.
- In production, `ProductionSecurityValidator` will refuse to start if the provided bootstrap password is too weak.

See PLAN.md or this file for security model.
