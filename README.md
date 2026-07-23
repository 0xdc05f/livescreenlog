# LiveScreenLog

Self-hosted **session replay** server: capture browser sessions with rrweb, store them in PostgreSQL, live-tail with Redis/Valkey, and review them in a built-in dashboard.

Java 21 · Spring Boot 4.x · PostgreSQL 16+ · Valkey/Redis · Svelte dashboard · Browser SDK


---

## 🚀 Real Production Setup (5 steps — this is the recommended way)

**Vultr Postgres + Docker Valkey + one Docker image = done.**

1. Create a PostgreSQL database on **Vultr** (or any managed Postgres).
2. Start Valkey (one command):
   ```bash
   docker run -d --name valkey -p 6379:6379 valkey/valkey:alpine
   ```
3. Run LiveScreenLog (published image, no build needed):
   ```bash
   docker run -d \
     -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e DB_HOST=your-vultr-db-host \
     -e DB_PORT=5432 \
     -e DB_NAME=livescreenlog \
     -e DB_USER=postgres \
     -e DB_PASSWORD=your-strong-db-password \
     -e REDIS_HOST=localhost \
     -e REDIS_PORT=6379 \
     -e LIVESCREENLOG_HMAC_SECRET='a-very-strong-random-string-at-least-32-chars' \
     -e LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://your-site.com,https://admin.your-site.com' \
     ghcr.io/livescreenlog/server:0.1.0
   ```
4. Open the dashboard → **Settings → Project Management** → create a project and **copy the API Key**.
5. Use it in your frontend:
   ```js
   import { LiveScreenLog } from 'livescreenlog';
   LiveScreenLog.init({
     apiKey: 'YOUR_API_KEY',
     dsn: 'https://your-livescreenlog-host',
     id: user.id,
   });
   ```

**That's literally it.**

Full step-by-step (with screenshots-style commands):  
[docs/deploy/PRODUCTION_GUIDE.md](docs/deploy/PRODUCTION_GUIDE.md)

---

## Features

- **Ingest API** — session create, event batch append, heartbeat, stop (HMAC session tokens)
- **Search & replay** — filter sessions, paginated events, rrweb player UI
- **Live tailing** — Redis Pub/Sub + SSE
- **Recording modes** — project policies (e.g. always-on / error / on-demand push)
- **Privacy defaults** — input masking, retention job, rate limits, prod fail-fast on weak secrets

## Downloads

Pre-built artifacts (after the first `v*` release tag):

| Artifact | Latest |
|----------|--------|
| Server JAR | [livescreenlog.jar](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar) |
| Browser JS | [livescreenlog.js](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js) |
| npm SDK | [`npm i livescreenlog`](https://www.npmjs.com/package/livescreenlog) |
| All versions | [Releases](https://github.com/0xdc05f/livescreenlog/releases) |

```bash
# Frontend (Vue 3 / React / Svelte / plain JS)
npm install livescreenlog rrweb
```

```js
// Vue 3 example
import { LiveScreenLog } from 'livescreenlog';

LiveScreenLog.init({
  dsn: 'https://your-livescreenlog-host',
  apiKey: 'YOUR_PROJECT_API_KEY',
  id: user.id,
});
LiveScreenLog.setTags({ dept: user.dept });
```

CDN (no build tool):
```html
<script src="https://cdn.jsdelivr.net/npm/livescreenlog@0.1.0/dist/livescreenlog.js"></script>
<script>
  LiveScreenLog.init({ dsn: '...', apiKey: '...', id: 'user-001' });
</script>
```

```bash
curl -fsSL -o livescreenlog.jar \
  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar
# JRE 21 + Postgres + Valkey/Redis + env — see Manual.md
java -jar livescreenlog.jar
```

Maintainers: [docs/release/RELEASE.md](docs/release/RELEASE.md) · tag `vX.Y.Z` runs CI publish.

## Quick start (local test only)

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
docker compose -f deploy/docker-compose.yml --env-file .env up -d
```

- Dashboard: `http://localhost:8080/`
- Health: `http://localhost:8080/actuator/health`

**For real production** (Vultr Postgres + Docker Valkey + GHCR image), use the 5-step guide above.

## Configuration

Copy `.env.example` → `.env`. **Never commit real secrets.**

| Variable | Purpose | Editable in Dashboard |
|----------|---------|-----------------------|
| `LIVESCREENLOG_HMAC_SECRET` | Signing secret for session tokens (**required strong value in prod**) | **No** — env only |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | CORS allow-list (no `*` in prod) | **Yes** (Settings → Server Config) |
| `LIVESCREENLOG_PROJECT_KEY` | Optional global fallback project key | **Yes** |
| `LIVESCREENLOG_RETENTION_DAYS` | Auto-delete old sessions (default `30`; `0` disables) | **Yes** |
| `DB_*` / `REDIS_*` | Database and cache (prod profile) | No |

**Live settings UI**: Dashboard → Settings → **Server Config** (retention, CORS origins, global project key). Changes take effect immediately without restart.

HMAC secret and database credentials must always come from environment / secret manager.

Full bilingual guide (한국어 + English): [Manual.md](Manual.md)

Details: `docs/security/SECURITY.md`, `docs/deploy/DEPLOY.md`.

## Project layout

```
├── src/main/java          Spring Boot server
├── src/main/resources     Config, Flyway
├── frontend/              Svelte dashboard (Vite)
├── sdk/                   Browser capture SDK (livescreenlog)
├── deploy/                Reference Dockerfile + compose (not the only way)
├── docs/                  Architecture, API, security, deploy, release
├── .github/workflows/     CI — tag v* → GitHub Release (JAR + JS)
├── version.json           Release version pin
├── release.sh             Sync versions + local artifact build
└── .env.example           Env template (no real secrets)
```

## Documentation

**한국어 + English 풀 매뉴얼**: [Manual.md](Manual.md) — 운영자 가이드, 연동, 보안, 배포를 모두 담고 있습니다.

| Doc | Description |
|-----|-------------|
| [Manual.md](Manual.md) | **Full bilingual manual (한국어 + English)** — 운영, 설정, 연동, 보안, 배포 |
| [docs/release/RELEASE.md](docs/release/RELEASE.md) | Versioning, tags, GitHub Release artifacts |
| [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md) | Self-host deploy reference |
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
