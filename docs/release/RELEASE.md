# Release management · 릴리스 관리

How binary artifacts (JAR, browser JS) are versioned and published.  
JAR / 브라우저 JS 버전 관리와 배포 방법.

## Artifact channels · 배포 채널

| Consumer · 사용자 | Channel · 채널 | Artifact · 산출물 |
|-------------------|----------------|-------------------|
| Operator · 운영자 | [GitHub Releases](https://github.com/0xdc05f/livescreenlog/releases) | `livescreenlog.jar` |
| Web integrator · 웹 연동 | Same release **or** self-hosted `/livescreenlog.js` | `livescreenlog.js` |
| Bundler / npm | npm (optional) `@livescreenlog/browser` | package from `sdk/` |
| Contributor · 기여자 | This git repo | source |

Sources stay in git. **Do not commit** `build/`, `sdk/dist/`, or jars (see `.gitignore`).

## Version source of truth · 버전 단일 출처

```
version.json          ← edit this
        │
        ▼
  ./release.sh --sync-only
        │
        ├── build.gradle
        ├── sdk/package.json
        └── frontend/package.json
```

Tag must match: **`v` + version.json**  
Example: `"version": "1.0.0"` → tag `v1.0.0`.

## Local release prep · 로컬 준비

```bash
# 1) Bump version
# edit version.json → "1.0.1"

# 2) Sync metadata + optional local build
./release.sh --sync-only     # versions only
./release.sh                 # sync + bootJar + sdk build

# 3) Commit on develop, merge to main (as you prefer)
git add version.json build.gradle sdk/package.json frontend/package.json
git commit -m "chore: release 1.0.1"
git push origin develop
# merge to main…

# 4) Tag from the commit you want to ship (usually main)
git checkout main && git pull
git tag -a v1.0.1 -m "v1.0.1"
git push origin v1.0.1
```

Pushing the tag runs [`.github/workflows/release.yml`](../../.github/workflows/release.yml):

1. Verify tag == `v` + `version.json`
2. `./gradlew bootJar` (dashboard + SDK inside the jar)
3. Build `sdk/dist/livescreenlog.js`
4. Upload to GitHub Release:
   - `livescreenlog.jar` / `livescreenlog.js` (stable names for “latest” URLs)
   - `livescreenlog-{version}.jar` / `.js`
   - `SHA256SUMS.txt`

## Download URLs · 다운로드 URL

| Link | URL |
|------|-----|
| Releases page | https://github.com/0xdc05f/livescreenlog/releases |
| Latest JAR | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar |
| Latest JS | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js |
| Specific version | `…/releases/download/v1.0.0/livescreenlog-1.0.0.jar` |

Until the first tag is pushed, “latest” links 404 — cut `v1.0.0` when ready.

## npm (optional) · npm (선택)

```bash
cd sdk
npm ci && npm run build
npm publish --access public
```

Prefer publishing the same version as the git tag. Not required for self-host: operators can serve JS from the jar (`/livescreenlog.js`) or the Release asset.

## Docker image (optional) · Docker (선택)

```bash
./gradlew bootJar
docker build -f deploy/Dockerfile -t ghcr.io/0xdc05f/livescreenlog:1.0.0 .
```

Automating GHCR push can be added to the release workflow later.

## Layout reminder · 구조

```
version.json                 version pin
release.sh                   sync (+ local build)
.github/workflows/release.yml  tag → GitHub Release
deploy/                      reference runtime only
sdk/                         browser package source
frontend/                    dashboard source
dist/release/                local staging only (gitignored)
```
