# LiveScreenLog Security Guidelines

## 1. Authentication & Authorization

### 1.1 Ingest API
- **Targets**: `POST /api/sessions`, `POST /api/events`, `POST /api/heartbeat`, `POST /api/stop`
- **Session create**: body `projectKey` must match a registered project API key or `LIVESCREENLOG_PROJECT_KEY`.
- **Subsequent calls**: header `x-livescreenlog-session-token` (HMAC-SHA256, expiry). Verified by `HmacAuthenticationFilter` → `ROLE_SESSION`.
- **Rate limits** (Redis): create per IP+projectKey, append per sessionId (`livescreenlog.rate-limit.*`).

### 1.2 Read / Project / Push admin API
- Dashboard uses custom login page (`/login`, `/login.html` via forward) with users from DB (SUPER_ADMIN / ADMIN roles).
- Admin paths now protected: `/api/sessions/**`, `/api/projects/**`, `/`, `/index.html` require hasAnyRole("SUPER_ADMIN", "ADMIN").
- POST /api/sessions is permitAll (projectKey validated in SessionIngestionService); GET /api/sessions/** remains ADMIN.
- GET /api/stats/** requires ADMIN.
- Ingest: HMAC for `/api/events`, `/api/heartbeat`, `/api/stop` (ROLE_SESSION).
- `/api/push/**` (and GET /api/push/connect) remain permitAll for SDK/ops (projectKey validated in app).
- Still recommend network isolation or reverse proxy for production exposure of dashboard.
- `requestCache` is disabled; authenticated GET /login forwards to dashboard. `login.html` does a fetch to `/api/me` to decide.
- HMAC filter restores the previous SecurityContext (does not overwrite dashboard JSESSIONID).

### 1.3 Push connect (SDK)
- `GET /api/push/connect` is public but requires a valid `projectKey`.
- Mode B push admin paths work without custom login page so operators can trigger recording without dashboard credentials.

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
