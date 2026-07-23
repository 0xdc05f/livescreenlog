# LiveScreenLog

Self-hosted **session replay** server: capture browser sessions with rrweb, store them in PostgreSQL, live-tail with Redis/Valkey, and review them in a built-in dashboard.

Java 21 · Spring Boot 4.x · PostgreSQL 16+ · Valkey/Redis · Svelte dashboard · Browser SDK

> Related / upstream inspiration: [s2-streamstore/sessionlens](https://github.com/s2-streamstore/sessionlens) · [0xdc05f/session-replay](https://github.com/0xdc05f/session-replay)

## Features

- **Ingest API** — session create, event batch append, heartbeat, stop (HMAC session tokens)
- **Search & replay** — filter sessions, paginated events, rrweb player UI
- **Live tailing** — Redis Pub/Sub + SSE
- **Recording modes** — project policies (e.g. always-on / error / on-demand push)
- **Privacy defaults** — input masking, retention job, rate limits, prod fail-fast on weak secrets

## Quick start

### Requirements

- Java 21+ (JRE enough to run a built jar; JDK to build)
- Docker (optional — for Postgres + Valkey)
- Node.js 20+ (only if you rebuild frontend/SDK)

### Run locally

```bash
cp .env.example .env   # optional; set secrets for non-default local runs
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

| Endpoint | URL |
|----------|-----|
| Health | http://localhost:8080/actuator/health |
| Dashboard | http://localhost:8080/ |
| Sample capture pages | http://localhost:8080/sample.html (after a full build that copies static assets) |

### Full stack (reference compose)

`deploy/` holds **example** Docker files — adapt for your environment. Guide: [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md).

```bash
cp .env.example .env
# set LIVESCREENLOG_HMAC_SECRET (32+ chars) and LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS
./gradlew bootJar
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

### Build a release jar

```bash
./gradlew bootJar
# artifact: build/libs/*.jar
```

This builds the Svelte frontend and browser SDK, then packs them into Spring static resources.

## Configuration

Copy `.env.example` → `.env`. Important variables:

| Variable | Purpose |
|----------|---------|
| `LIVESCREENLOG_HMAC_SECRET` | Signing secret for session tokens (**required strong value in prod**) |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | CORS allow-list (no `*` in prod) |
| `LIVESCREENLOG_PROJECT_KEY` | Optional global fallback project key |
| `LIVESCREENLOG_RETENTION_DAYS` | Auto-delete old sessions (default `30`; `0` disables) |
| `DB_*` / `REDIS_*` | Used by `prod` profile and Docker app service |

Details: `docs/security/SECURITY.md`, `docs/deploy/DEPLOY.md`, `src/main/resources/application*.yml`.

## Project layout

```
├── src/main/java          Spring Boot server
├── src/main/resources     Config, Flyway
├── frontend/              Svelte dashboard (Vite)
├── sdk/                   Browser capture SDK (@livescreenlog/browser)
├── deploy/                Reference Dockerfile + compose (not the only way)
├── docs/                  Architecture, API, security, deploy guide
├── sample*.html           Demo capture pages (repo root)
└── .env.example           Env template (no real secrets)
```

## Documentation

| Doc | Description |
|-----|-------------|
| [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md) | **Beta / self-host deploy reference** |
| [docs/api/API.md](docs/api/API.md) | HTTP API |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | Components & data flow |
| [docs/schema/SCHEMA.md](docs/schema/SCHEMA.md) | PostgreSQL schema |
| [docs/security/SECURITY.md](docs/security/SECURITY.md) | Auth, CORS, masking, retention |
| [docs/OPENSOURCE.md](docs/OPENSOURCE.md) | What must not be committed + publish checklist |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Dev setup & PRs |

## Security notice

Session recordings may contain personal data. Do not expose read/admin APIs on the public internet without network controls. Never commit real `.env` files or production keys. See [SECURITY.md](SECURITY.md) and [docs/OPENSOURCE.md](docs/OPENSOURCE.md).

## License

[MIT](LICENSE)
