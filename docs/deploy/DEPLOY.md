# Deploy reference (beta / self-host)

This is a **recommended shape**, not a one-size production recipe.  
Wire it to your reverse proxy, secrets store, managed Postgres/Redis, and network policy.

Runtime pieces:

| Component | Role |
|-----------|------|
| App JAR (JRE 21) | API + dashboard + static SDK |
| PostgreSQL 16+ | Sessions / events (Flyway on startup) |
| Valkey or Redis | Live tail Pub/Sub + rate limits |

You do **not** need JDK, Node, or Gradle on the target host if you ship a pre-built jar.

## 1. Build the jar (CI or laptop)

```bash
./gradlew bootJar
# → build/libs/*.jar
```

## 2. Minimal environment

Copy `.env.example` → `.env` (never commit `.env`).

| Variable | Required in `prod` | Notes |
|----------|--------------------|--------|
| `LIVESCREENLOG_HMAC_SECRET` | Yes | ≥ 32 random characters |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | Yes | Comma-separated origins; no `*` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Yes | Postgres |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Host/port yes | Password optional |
| `LIVESCREENLOG_RETENTION_DAYS` | No | Default `30`; `0` disables cleanup |
| `LIVESCREENLOG_PROJECT_KEY` | No | Global fallback project key |

Prod refuses to start on weak HMAC or wildcard capture origins.  
See `docs/security/SECURITY.md`.

## 3. Reference Docker Compose

Files live under `deploy/` so root stays clean and operators can replace them freely.

```bash
# repo root
cp .env.example .env
# edit secrets + origins
./gradlew bootJar
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

Infra only (app via `./gradlew bootRun` on the host):

```bash
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

### What the reference does

- Postgres 16 + Valkey + app image from `deploy/Dockerfile`
- App profile: `prod`
- Local default DB user/password — **change before any shared host**
- Compose HMAC default is only for local smoke tests — **replace in beta**

### What you should change for real beta

1. Strong unique `LIVESCREENLOG_HMAC_SECRET`
2. Real capture origins (dashboard + sites that load the SDK)
3. DB/Redis credentials and **do not publish** 5432/6379 publicly
4. TLS terminator (Caddy / nginx / cloud LB) in front of `:8080`
5. Disk for Postgres (event JSONB grows with traffic)
6. Single instance for Mode B push (process-local emitters)

## 4. Recommended bare-metal / VM shape

```text
[Internet]
    → reverse proxy (TLS)
        → 127.0.0.1:8080  java -jar livescreenlog.jar
    Postgres   (private network / localhost)
    Valkey     (private network / localhost)
```

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=... DB_USER=... DB_PASSWORD=...
export REDIS_HOST=...
export LIVESCREENLOG_HMAC_SECRET='...'
export LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://app.example.com,https://lsl.example.com'
java -jar build/libs/livescreenlog-*.jar
```

(Exact jar name follows `version` in `build.gradle` / `version.json`.)

## 5. Dockerfile note

`deploy/Dockerfile` expects a **pre-built** jar in `build/libs/`.  
It is intentionally thin so you can:

- build jar in CI, then `docker build -f deploy/Dockerfile`
- or skip Docker for the app and run the jar under systemd

Multi-stage “build inside Docker” images are fine for your org — treat this file as the runtime contract (JRE 21, port 8080, `prod` profile).

## 6. Health & smoke

| Check | URL |
|-------|-----|
| Health | `GET /actuator/health` |
| Dashboard | `GET /` |

## 7. Operator checklist (beta)

- [ ] Secrets only via env / secret manager (not baked into image)
- [ ] Capture origins allow-list matches real sites
- [ ] Read/admin APIs not exposed without network auth
- [ ] Backups for Postgres volume
- [ ] Retention days set for privacy policy
- [ ] SDK clients use `/livescreenlog.js` and `x-livescreenlog-session-token`
