# Contributing

Thanks for helping improve LiveScreenLog.

## Development setup

Requirements:

- Java 21+
- Node.js 20+ (frontend + SDK)
- Docker (PostgreSQL 16 + Valkey/Redis)

```bash
git clone <repo-url>
cd livescreenlog
cp .env.example .env   # optional; edit secrets for local runs
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

Deploy reference (compose / bare metal): `docs/deploy/DEPLOY.md`.

- Health: `GET http://localhost:8080/actuator/health`
- API reference: `docs/api/API.md`
- Architecture: `docs/architecture/ARCHITECTURE.md`
- Security model: `docs/security/SECURITY.md`

Frontend (Vite dev server):

```bash
cd frontend && npm install && npm run dev
```

SDK:

```bash
cd sdk && npm install && npm run build
```

Full jar (builds FE + SDK into static resources):

```bash
./gradlew bootJar
```

## Version sync & release artifacts

`version.json` is the source of truth. After changing it:

```bash
./release.sh --sync-only   # metadata only
./release.sh               # sync + local JAR/JS into dist/release/
```

Publishing binaries: tag `vX.Y.Z` (must match `version.json`) and push — CI creates a [GitHub Release](https://github.com/0xdc05f/livescreenlog/releases). See [docs/release/RELEASE.md](docs/release/RELEASE.md).

## Pull requests

1. Fork and branch from the default branch.
2. Keep changes focused; include tests when changing API or security behavior.
3. Do not commit secrets, local agent state, or build outputs (see `.gitignore` and `docs/OPENSOURCE.md`).
4. Update docs under `docs/` when behavior changes.
5. Describe the problem, solution, and how you verified it.

## Code style

- Java 21, Spring Boot 4.x, Virtual Threads enabled.
- Prefer records for DTOs; Flyway-only schema changes.
- Browser capture defaults should stay privacy-safe (`maskAllInputs`, block/ignore classes).

## License

By contributing, you agree that your contributions are licensed under the MIT License (see `LICENSE`).
