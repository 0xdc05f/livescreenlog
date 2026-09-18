# LiveScreenLog Manual

> Version aligned with repo `version.json` · License: MIT  
> Repo: https://github.com/0xdc05f/livescreenlog

### Downloads

Prefer pre-built artifacts from GitHub Releases (available after the first beta `v*` tag, e.g. `v0.3.1`).

| Artifact | Latest | Notes |
|----------|--------|-------|
| **Server JAR** | [livescreenlog.jar](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar) | JRE 25 + Postgres + Valkey/Redis |
| **Browser JS** | [livescreenlog.js](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js) | UMD · `window.LiveScreenLog` |
| **All releases** | [Releases page](https://github.com/0xdc05f/livescreenlog/releases) | Versioned JAR/JS + `SHA256SUMS.txt` |
| **npm SDK** (optional) | [`livescreenlog`](https://www.npmjs.com/package/livescreenlog) | rrweb is bundled inside; or use `/livescreenlog.js` |

```bash
# Operator — download JAR and run (after configuring DB/Redis/env)
curl -fsSL -o livescreenlog.jar \
  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar
java -jar livescreenlog.jar
```

```html
<!-- Integrator — pin a version or use your server’s /livescreenlog.js -->
<script src="https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js"></script>
```

In production, prefer your server’s `/livescreenlog.js` or a **version-pinned** Release URL.

Release process for maintainers: [docs/release/RELEASE.md](docs/release/RELEASE.md)

---

## Table of contents

1. [Overview](#1-overview)
2. [Requirements](#2-requirements)
3. [Quick start](#3-quick-start)
4. [Configuration](#4-configuration)
5. [Deploy](#5-deploy)
6. [Dashboard](#6-dashboard)
7. [SDK integration](#7-sdk-integration)
8. [Recording modes](#8-recording-modes)
9. [HTTP API summary](#9-http-api-summary)
10. [Security & privacy](#10-security--privacy)
11. [Operations](#11-operations)
12. [Troubleshooting](#12-troubleshooting)
13. [Further docs](#13-further-docs)

---

## 1. Overview

LiveScreenLog is a **self-hosted** server for capturing, storing, and replaying browser sessions.  
It records DOM activity with rrweb, persists data in PostgreSQL, and live-tails via Valkey/Redis.  
A Svelte dashboard and browser SDK (`livescreenlog` / `/livescreenlog.js`) are included. (rrweb is bundled in the npm package.)

| Component | Role |
|-----------|------|
| App (JRE 25 / Spring Boot) | API, dashboard, static SDK |
| PostgreSQL 16+ | Session metadata & events (Flyway) |
| Valkey or Redis | Pub/Sub live tail, rate limits |
| Browser SDK | Capture + push (Mode B) client |

---

## 2. Requirements

**Runtime on the host**
- JRE 25+ (JDK 25 if you build)
- PostgreSQL 16+
- Valkey or Redis
- Optional: Docker, reverse proxy (TLS)

**Build machine only**
- Node.js 22+ (rebuild FE/SDK)
- Gradle Wrapper (`./gradlew`)

---

## 3. Quick start

### 3.0 Pre-built JAR (operators)

No source build. Needs JRE 25, Postgres, Valkey/Redis, and env vars.

```bash
curl -fsSL -o livescreenlog.jar \
  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar
# set SPRING_PROFILES_ACTIVE=prod and secrets — see §4 / docs/deploy/DEPLOY.md
java -jar livescreenlog.jar
```

| Download | URL |
|----------|-----|
| Latest JAR | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar |
| Latest JS | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js |
| Releases | https://github.com/0xdc05f/livescreenlog/releases |

### 3.1 Local dev

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

| Check | URL |
|-------|-----|
| Health | http://localhost:8080/actuator/health |
| Dashboard | http://localhost:8080/ |

If the dashboard static assets are missing, run `./gradlew bootJar` (or `copySdkToStatic`) first.

### 3.2 Full stack (reference compose)

```bash
cp .env.example .env
# edit LIVESCREENLOG_HMAC_SECRET (≥32 chars) and LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS
./gradlew bootJar
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

Files under `deploy/` are a **reference**, not the only production layout.  
See [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md).

---

## 4. Configuration

Copy `.env.example` → `.env`. **Never commit a real `.env`.**  
The `prod` profile **refuses to start** on weak HMAC or `*` capture origins.

| Variable | Required in prod | Description |
|----------|------------------|-------------|
| `LIVESCREENLOG_HMAC_SECRET` | Yes | Session token signing secret, ≥32 chars |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | Yes | CORS allow-list, comma-separated, no `*` |
| `LIVESCREENLOG_PROJECT_KEY` | No | Optional global fallback project key |
| `LIVESCREENLOG_RETENTION_DAYS` | No | Auto-delete age (default 30; `0` = off) |
| `LIVESCREENLOG_RATE_LIMIT_CREATE` | No | Session create / min |
| `LIVESCREENLOG_RATE_LIMIT_EVENTS` | No | Event append / min |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | Yes (prod) | PostgreSQL |
| `REDIS_HOST` `REDIS_PORT` `REDIS_PASSWORD` | Host/port yes | Valkey/Redis |

YAML prefix: `livescreenlog.*`  
Files: `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`

---

## 5. Deploy

### 5.1 Runtime shape

```
[Internet]
    → reverse proxy (TLS)
        → app :8080  (java -jar …)
    PostgreSQL   (private)
    Valkey/Redis (private)
```

On the target host you only need **JRE 25 + jar + Postgres + Valkey/Redis**.  
JDK, Node, and Gradle stay on the build/CI machine.

### 5.2 Bare metal

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=... DB_USER=... DB_PASSWORD=...
export REDIS_HOST=...
export LIVESCREENLOG_HMAC_SECRET='…'   # ≥32 chars
export LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://app.example.com,https://lsl.example.com'
java -jar build/libs/*.jar
```

### 5.3 Docker image

```bash
./gradlew bootJar
docker build -f deploy/Dockerfile -t livescreenlog:local .
```

The Dockerfile is a thin runtime image that **copies a pre-built jar**.

### 5.4 Beta checklist

- [ ] Strong unique `LIVESCREENLOG_HMAC_SECRET`
- [ ] Real capture origins (no `*`)
- [ ] DB/Redis not exposed publicly
- [ ] TLS in front of the app
- [ ] Postgres disk & backups
- [ ] Retention matches privacy policy
- [ ] Read/admin APIs network-isolated

---

## 6. Dashboard

Open the server origin in a browser (`http://host:8080/`).  
Use the language selector (한국어 / English) for UI strings.

| Area | Description |
|------|-------------|
| Replay | List, search, playback. Live filter (heartbeat in last 5 min). Live scrubber uses wall clock. Recommendation chips = error sessions |
| Settings | Projects, guide, server config, users, account (password), **Usage** |
| Top bar | Logo → Replay, language, change password, log out |

URLs like `/?view=settings&tab=stats`. Browser Back switches settings↔replay. Login is not kept in history (`location.replace`).

**Auth**

- Default dev account: `admin` / `admin-password-need-to-change` (bootstrapped when `users` table empty). Change in Settings → account.
- The first SUPER_ADMIN password is printed once in the server log `FIRST BOOT` banner.
- `POST /api/sessions` permitAll (projectKey validated server-side). GET sessions/dashboard require ADMIN.
- HMAC only for `/api/events`, `/api/heartbeat`, `/api/stop` and is **request-scoped** (does not overwrite dashboard JSESSIONID). SDK uses `credentials: 'omit'`.
- Dashboard cookie: 8h, HttpOnly, SameSite=Lax.
- Idle: no heartbeat for 30min → STOPPED. HMAC token valid 24h.
- `/api/push/**` public (projectKey validated). Use with care in prod.
- Details: [docs/security/SECURITY.md](docs/security/SECURITY.md)

---

## 7. SDK integration

### 7.1 Create a project

Dashboard → Settings → Projects: create a project and copy the **API Key**.

### 7.2 Browser script tag

**Preferred:** serve from your LiveScreenLog server (same origin as API).  
**Alternative:** [Release JS](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js) (pin a version in production).

```html
<script src="https://YOUR-LSL-HOST/livescreenlog.js"></script>
<!-- or: https://github.com/0xdc05f/livescreenlog/releases/download/v0.3.1/livescreenlog-0.3.1.js -->
<script>
  LiveScreenLog.init({
    apiKey: 'YOUR_PROJECT_API_KEY',
    dsn: 'https://YOUR-LSL-HOST',
    id: 'user-001',
    onSessionReady: function (sessionId) {},
    onInitError: function (err) {},
    onStandby: function (mode) {}  // Mode B/C standby
  });
</script>
```
Note: dynamic CDN fallback uses `rrweb@2.1.4`. Bundled SDK includes rrweb 2.1.4.

### 7.3 npm / ESM

```bash
npm i livescreenlog
# rrweb is included as a dependency — no need to install it separately
```

```js
import { LiveScreenLog } from 'livescreenlog';

LiveScreenLog.init({
  apiKey: 'YOUR_PROJECT_API_KEY',
  dsn: 'https://YOUR-LSL-HOST',
  id: 'user-001'
});
```

### 7.4 Important names

| Item | Value |
|------|-------|
| Global | `window.LiveScreenLog` |
| Bundle URL | `/livescreenlog.js` |
| Auth header | `x-livescreenlog-session-token` |
| Mask block class | `livescreenlog-block` |
| Mask ignore class | `livescreenlog-ignore` |

Add `class="livescreenlog-block"` on sensitive UI to block capture.  
SDK defaults include `maskAllInputs: true`.

### 7.5 Local demos

Server serves `/sample-a.html` (targeted users), `/sample-b.html` (remote trigger), `/sample-c.html` (error), `/sample-all.html` (record all). Root copies may be gitignored; if present they are copied to static on build.

---

## 8. Recording modes

| Mode | Description |
|------|-------------|
| **ALL** | Record all users |
| **NONE** | Recording disabled |
| **A** | Only listed user IDs |
| **B** | Admin remote-triggers a connected client |
| **C** | Starts on client error events |

**Mode B constraint**  
The push emitter map is **process-local** (single instance).  
Multi-node needs sticky sessions or a shared fan-out design.

---

## 9. HTTP API summary

Base URL = server origin (e.g. `https://lsl.example.com`).  
Full detail: [docs/api/API.md](docs/api/API.md)

### Ingest (HMAC after create)

| Method | Path | Note |
|--------|------|------|
| `POST` | `/api/sessions` | Body: `projectKey`, `userId` / id fields (permitAll; projectKey validated server-side) |
| `POST` | `/api/events` | Header `x-livescreenlog-session-token` · rrweb batch |
| `POST` | `/api/heartbeat` | Keep-alive |
| `POST` | `/api/stop` | Mark stopped |

### Read (network-protect)

| Method | Path | Note |
|--------|------|------|
| `GET` | `/api/sessions` | `status`, `updatedAfter` etc. (live filter) |
| `GET` | `/api/sessions/live` | List SSE |
| `GET` | `/api/sessions/recommended?limit=` | Error recommendations |
| `GET` | `/api/sessions/{id}` | Metadata |
| `GET` | `/api/sessions/{id}/events` | Dashboard uses `paged=true` by default |
| `GET` | `/api/sessions/{id}/live` | Per-session SSE tail |
| `GET` | `/api/stats/overview?projectKey=` | Usage stats |
| `GET` | `/api/me` | Current user |
| `POST` | `/api/me/password` | Change password |

### Projects & push

| Method | Path | Note |
|--------|------|------|
| `*` | `/api/projects/**` | CRUD / settings |
| `GET` | `/api/push/connect` | SDK standby SSE |
| `GET` | `/api/push/active-terminals` | Mode B list |
| `POST` | `/api/push/trigger-record` | Mode B start |

### Ops

| Method | Path |
|--------|------|
| `GET` | `/actuator/health` |

---

## 10. Security & privacy

Recordings may contain personal or business data (DOM, inputs before mask, URLs, userIds).  
Apply retention and access controls that match your legal and internal policy.

| Topic | Guidance |
|-------|----------|
| Ingest auth | HMAC session token after create |
| Dashboard | Network isolation, not public internet |
| CORS | Explicit origins in prod |
| Masking | `maskAllInputs`, block/ignore classes |
| Retention | `LIVESCREENLOG_RETENTION_DAYS` |
| Secrets | Env / secret manager only |
| HMAC scope | Filter restores prior SecurityContext (preserves dashboard session) |
| Login | Uses fetch + `location.replace('/')` — Back does not return to login |
| Password | Settings → account / `POST /api/me/password` |

Report vulnerabilities: [SECURITY.md](SECURITY.md)

---

## 11. Operations

| Topic | Guidance |
|-------|----------|
| Schema | Flyway runs on startup |
| Retention job | Daily cleanup of old sessions |
| Rate limits | Redis-backed create/event limits |
| Scaling | Mode B assumes one instance |
| Backups | Back up the Postgres volume |
| Logs | Use app + proxy logs for incidents |
| Version | `version.json` + `./release.sh` — Sync versions with `release.sh` |

---

## 12. Troubleshooting

| Symptom | Check |
|---------|-------|
| App won't start in prod | HMAC length ≥32; origins set and not `*` |
| SDK not recording | API key, `id`, CORS origin, mode not NONE |
| Sample recording blocked | Project key/targetUsers, secret + hard refresh. Distinguish connect fail vs policy block |
| bootRun 8080 in use | `lsof -i :8080` then kill the PID |
| `/?continue` shows blank | Request cache disabled; `/` forwards to index.html |
| Back button looks like logout | Login uses history replace. Back in settings goes to replay |
| No sessions in live list | Check updatedAt within 5 min + heartbeat reaches DB |
| Recommendation rail empty | Normal if no error sessions (rail is hidden) |
| CORS errors | Add exact page origin to `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` |
| 401 on events | Send `x-livescreenlog-session-token` from create response |
| Empty dashboard static | Run `./gradlew bootJar` so FE/SDK are packed |
| DB connection failed | `DB_*`, Postgres up, network |
| Live tail silent | Redis/Valkey up; session still ACTIVE |
| Mode B no terminals | Client called init + push connect; same app instance |
| Old SessionLens clients | Migrate to `livescreenlog.js` / new header names |
| docker load missing `package/json` | `.tgz` is npm. Load only `*-image.tar.gz` |
| Image fails with exec format error | Confirm the tar is linux/amd64. Not a Mac arm64 image |
| `No setter found for property: dashboard-enabled` or `allowed-capture-origins` | 0.3.2 uses `LIVESCREENLOG_SECURITY_DASHBOARD_*`, `LIVESCREENLOG_SECURITY_HMAC_SECRET`, `LIVESCREENLOG_SECURITY_ALLOWED_CAPTURE_ORIGINS`. The same names without `SECURITY_` fail to start |
| Profile is `dev` | Use `SPRING_PROFILES_ACTIVE=prod` even on a dev server. Remove `dev` from compose environment and env files |
| UnknownHostException: postgres | Use compose service names (`shared-db`, `valkey`). Same `networks:` (e.g. devops-net). `docker exec <app> getent hosts <service>` |
| DB port | On the same network use **5432**. Do not use host mapping 15432 for container-to-container traffic |
| Port 8090 but Tomcat 8080 | `8090:8080` is correct. `8090:8090` needs `SERVER_PORT=8090`. CORS origin must match the browser port |
| FILE appender / no log file | Make `/data/livescreenlog/logs` writable by the container user. `user: "uid:gid"` or `chown 100:100` |
| Env password login fails | Created only when `users` is empty on first boot. Env is ignored after that. Check the FIRST BOOT banner in `docker logs`. If users already exist: `--create-admin=user:pass:SUPER_ADMIN` |
| CORS | Browser address origin (host+port). `*` is forbidden in prod |

---

## 13. Further docs

| Doc | Content |
|-----|---------|
| [README.md](README.md) | Project overview |
| [GitHub Releases](https://github.com/0xdc05f/livescreenlog/releases) | JAR / JS downloads |
| [docs/release/RELEASE.md](docs/release/RELEASE.md) | How maintainers cut a release |
| [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md) | Deploy reference |
| [docs/api/API.md](docs/api/API.md) | HTTP API detail |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | Architecture |
| [docs/schema/SCHEMA.md](docs/schema/SCHEMA.md) | DB schema |
| [docs/security/SECURITY.md](docs/security/SECURITY.md) | Security model |
| [docs/OPENSOURCE.md](docs/OPENSOURCE.md) | Publish checklist |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contributors |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting |

---

## License

MIT — see [LICENSE](LICENSE).  
You may use, modify, and redistribute under the MIT terms; keep the copyright notice.
