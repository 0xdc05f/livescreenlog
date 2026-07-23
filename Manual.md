# LiveScreenLog Manual · 매뉴얼

| | |
|---|---|
| **KO** | 셀프호스팅 세션 리플레이 서버 운영·연동 안내 |
| **EN** | Operator & integration guide for the self-hosted session replay server |

> Version aligned with repo `version.json` · License: MIT  
> Repo: https://github.com/0xdc05f/livescreenlog

---

## Table of contents · 목차

1. [Overview · 개요](#1-overview--개요)
2. [Requirements · 요구 사항](#2-requirements--요구-사항)
3. [Quick start · 빠른 시작](#3-quick-start--빠른-시작)
4. [Configuration · 설정](#4-configuration--설정)
5. [Deploy · 배포](#5-deploy--배포)
6. [Dashboard · 관리 화면](#6-dashboard--관리-화면)
7. [SDK integration · SDK 연동](#7-sdk-integration--sdk-연동)
8. [Recording modes · 녹화 모드](#8-recording-modes--녹화-모드)
9. [HTTP API summary · API 요약](#9-http-api-summary--api-요약)
10. [Security & privacy · 보안·개인정보](#10-security--privacy--보안개인정보)
11. [Operations · 운영](#11-operations--운영)
12. [Troubleshooting · 문제 해결](#12-troubleshooting--문제-해결)
13. [Further docs · 추가 문서](#13-further-docs--추가-문서)

---

## 1. Overview · 개요

**KO**  
LiveScreenLog는 브라우저 화면 세션을 수집·저장·재생하는 **셀프호스팅** 서버입니다.  
rrweb으로 DOM 변화를 캡처하고, PostgreSQL에 저장하며, Valkey/Redis로 라이브 테일링을 지원합니다.  
대시보드(Svelte)와 브라우저 SDK(`@livescreenlog/browser` / `/livescreenlog.js`)가 포함됩니다.

**EN**  
LiveScreenLog is a **self-hosted** server for capturing, storing, and replaying browser sessions.  
It records DOM activity with rrweb, persists data in PostgreSQL, and live-tails via Valkey/Redis.  
A Svelte dashboard and browser SDK (`@livescreenlog/browser` / `/livescreenlog.js`) are included.

| Component · 구성 | Role · 역할 |
|------------------|-------------|
| App (JRE 21 / Spring Boot) | API, dashboard, static SDK |
| PostgreSQL 16+ | Session metadata & events (Flyway) |
| Valkey or Redis | Pub/Sub live tail, rate limits |
| Browser SDK | Capture + push (Mode B) client |

---

## 2. Requirements · 요구 사항

**KO — 런타임(서버에 설치)**  
- JRE 21 이상 (실행만 할 때; 빌드 시 JDK 21)  
- PostgreSQL 16+  
- Valkey 또는 Redis  
- (선택) Docker, 리버스 프록시(TLS)

**EN — Runtime on the host**  
- JRE 21+ (JDK 21 if you build)  
- PostgreSQL 16+  
- Valkey or Redis  
- Optional: Docker, reverse proxy (TLS)

**KO — 빌드 머신에만 필요**  
Node.js 20+(프론트/SDK 재빌드), Gradle Wrapper(`./gradlew`)

**EN — Build machine only**  
Node.js 20+ (rebuild FE/SDK), Gradle Wrapper (`./gradlew`)

---

## 3. Quick start · 빠른 시작

### 3.1 Local dev · 로컬 개발

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

| Check · 확인 | URL |
|--------------|-----|
| Health | http://localhost:8080/actuator/health |
| Dashboard · 대시보드 | http://localhost:8080/ |

**KO**  
대시보드 정적 자산이 비어 있으면 `./gradlew bootJar` 또는 `copySdkToStatic` 후 실행하세요.

**EN**  
If the dashboard static assets are missing, run `./gradlew bootJar` (or `copySdkToStatic`) first.

### 3.2 Full stack (reference compose) · 전체 스택 예시

```bash
cp .env.example .env
# edit LIVESCREENLOG_HMAC_SECRET (≥32 chars) and LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS
./gradlew bootJar
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

**KO**  
`deploy/` 아래 파일은 **참고용**입니다. 환경에 맞게 네트워크·시크릿·TLS를 수정하세요.  
자세한 배포: [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md)

**EN**  
Files under `deploy/` are a **reference**, not the only production layout.  
See [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md).

---

## 4. Configuration · 설정

**KO**  
`.env.example`을 복사해 `.env`를 만듭니다. **실키 `.env`는 커밋하지 마세요.**  
`prod` 프로필은 약한 HMAC·와일드카드 origin(`*`)이면 **기동을 거부**합니다.

**EN**  
Copy `.env.example` → `.env`. **Never commit a real `.env`.**  
The `prod` profile **refuses to start** on weak HMAC or `*` capture origins.

| Variable · 변수 | Required in prod · prod 필수 | Description · 설명 |
|-----------------|------------------------------|---------------------|
| `LIVESCREENLOG_HMAC_SECRET` | Yes · 예 | Session token signing secret, ≥32 chars · 세션 토큰 서명 비밀키 |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | Yes · 예 | CORS allow-list, comma-separated, no `*` · 캡처 허용 Origin 목록 |
| `LIVESCREENLOG_PROJECT_KEY` | No · 아니오 | Optional global fallback project key · 전역 폴백 프로젝트 키 |
| `LIVESCREENLOG_RETENTION_DAYS` | No · 아니오 | Auto-delete age (default 30; `0` = off) · 보관 일수 |
| `LIVESCREENLOG_RATE_LIMIT_CREATE` | No · 아니오 | Session create / min · 분당 세션 생성 한도 |
| `LIVESCREENLOG_RATE_LIMIT_EVENTS` | No · 아니오 | Event append / min · 분당 이벤트 전송 한도 |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | Yes (prod) | PostgreSQL |
| `REDIS_HOST` `REDIS_PORT` `REDIS_PASSWORD` | Host/port yes | Valkey/Redis |

YAML prefix · YAML 접두사: `livescreenlog.*`  
Files · 파일: `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`

---

## 5. Deploy · 배포

### 5.1 Runtime shape · 권장 구성

```text
[Internet]
    → reverse proxy (TLS) · 리버스 프록시
        → app :8080  (java -jar …)
    PostgreSQL   (private · 비공개망)
    Valkey/Redis (private · 비공개망)
```

**KO**  
대상 서버에는 **JRE 21 + jar + Postgres + Valkey/Redis**면 충분합니다.  
JDK·Node·Gradle은 빌드 머신/CI에만 있으면 됩니다.

**EN**  
On the target host you only need **JRE 21 + jar + Postgres + Valkey/Redis**.  
JDK, Node, and Gradle stay on the build/CI machine.

### 5.2 Bare metal · jar 직접 실행

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=... DB_USER=... DB_PASSWORD=...
export REDIS_HOST=...
export LIVESCREENLOG_HMAC_SECRET='…'   # ≥32 chars
export LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://app.example.com,https://lsl.example.com'
java -jar build/libs/*.jar
```

### 5.3 Docker image · 이미지

```bash
./gradlew bootJar
docker build -f deploy/Dockerfile -t livescreenlog:local .
```

**KO**  
Dockerfile은 **미리 빌드된 jar**를 복사하는 얇은 런타임 이미지입니다.

**EN**  
The Dockerfile is a thin runtime image that **copies a pre-built jar**.

### 5.4 Beta checklist · 베타 체크리스트

- [ ] Strong unique `LIVESCREENLOG_HMAC_SECRET` · 강한 HMAC 비밀키  
- [ ] Real capture origins (no `*`) · 실제 Origin 허용 목록  
- [ ] DB/Redis not exposed publicly · DB/Redis 비공개  
- [ ] TLS in front of the app · 앱 앞단 TLS  
- [ ] Postgres disk & backups · 디스크·백업  
- [ ] Retention matches privacy policy · 보관 기간 정책 반영  
- [ ] Read/admin APIs network-isolated · 조회/관리 API 망 분리  

---

## 6. Dashboard · 관리 화면

**KO**  
브라우저에서 서버 origin(`http://host:8080/`)으로 접속합니다.  
상단 언어 선택(한국어 / English)으로 UI 문구를 바꿀 수 있습니다.

**EN**  
Open the server origin in a browser (`http://host:8080/`).  
Use the language selector (한국어 / English) for UI strings.

| Area · 영역 | KO | EN |
|-------------|----|----|
| Replay · 리플레이 | 세션 목록·검색·재생·라이브 | List, search, playback, live tail |
| Projects · 프로젝트 | API 키·녹화 모드 관리 | API keys & recording modes |
| Guide · 연동 가이드 | 클라이언트 스니펫 복사 | Copy client snippets |

**KO — 인증 주의**  
대시보드/조회/프로젝트 API는 앱 레벨 로그인 없이 열려 있습니다.  
**VPN·사설망·리버스 프록시 인증**으로 보호하세요. Ingest만 HMAC 토큰으로 보호됩니다.

**EN — Auth note**  
Dashboard / read / project APIs have **no app-level login**.  
Protect them with **VPN, private network, or reverse-proxy auth**. Only ingest uses HMAC tokens.

---

## 7. SDK integration · SDK 연동

### 7.1 Create a project · 프로젝트 생성

**KO**  
대시보드 → 설정 → 프로젝트 관리에서 프로젝트를 만들고 **API Key**를 복사합니다.

**EN**  
Dashboard → Settings → Projects: create a project and copy the **API Key**.

### 7.2 Browser script tag · 스크립트 태그

```html
<script src="https://YOUR-LSL-HOST/livescreenlog.js"></script>
<script>
  LiveScreenLog.init({
    apiKey: 'YOUR_PROJECT_API_KEY',
    dsn: 'https://YOUR-LSL-HOST',
    id: 'user-001'           // required user id · 필수 사용자 식별자
  });
  LiveScreenLog.setTags({ dept: 'sales' });
</script>
```

### 7.3 npm / ESM

```bash
npm i @livescreenlog/browser
# peer: rrweb
```

```js
import { LiveScreenLog } from '@livescreenlog/browser';

LiveScreenLog.init({
  apiKey: 'YOUR_PROJECT_API_KEY',
  dsn: 'https://YOUR-LSL-HOST',
  id: 'user-001'
});
```

### 7.4 Important names · 중요 식별자

| Item · 항목 | Value · 값 |
|-------------|------------|
| Global · 전역 객체 | `window.LiveScreenLog` |
| Bundle URL | `/livescreenlog.js` |
| Auth header · 인증 헤더 | `x-livescreenlog-session-token` |
| Mask block class | `livescreenlog-block` |
| Mask ignore class | `livescreenlog-ignore` |

**KO**  
민감 UI에 `class="livescreenlog-block"`을 주면 캡처에서 가려집니다.  
SDK 기본값은 `maskAllInputs: true`입니다.

**EN**  
Add `class="livescreenlog-block"` on sensitive UI to block capture.  
SDK defaults include `maskAllInputs: true`.

### 7.5 Local demos · 로컬 데모

**KO**  
루트 `sample*.html`은 **로컬 전용**(gitignore)입니다. 공개 저장소에는 포함되지 않습니다.  
있을 경우 빌드 시 static으로 복사되어 `/sample.html` 등으로 제공될 수 있습니다.

**EN**  
Root `sample*.html` files are **local-only** (gitignored) and are not published.  
If present, the build may copy them into static assets (`/sample.html`, etc.).

---

## 8. Recording modes · 녹화 모드

| Mode · 모드 | KO | EN |
|-------------|----|----|
| **ALL** | 모든 사용자 녹화 | Record all users |
| **NONE** | 녹화 안 함 | Recording disabled |
| **A** | 대상 userId 목록만 | Only listed user IDs |
| **B** | 관리자가 접속 단말에 녹화 지시 | Admin remote-triggers a connected client |
| **C** | `error` / `unhandledrejection` 시 시작 | Starts on client error events |

**KO — Mode B 제약**  
푸시 연결 목록은 **프로세스 로컬**입니다. 베타/단일 인스턴스를 전제로 하세요.  
스케일 아웃 시 sticky session 또는 별도 fan-out이 필요합니다.

**EN — Mode B constraint**  
The push emitter map is **process-local** (single instance).  
Multi-node needs sticky sessions or a shared fan-out design.

---

## 9. HTTP API summary · API 요약

Base URL = server origin (e.g. `https://lsl.example.com`).  
Full detail · 상세: [docs/api/API.md](docs/api/API.md)

### Ingest · 수집 (HMAC after create · 생성 후 HMAC)

| Method | Path | Note · 비고 |
|--------|------|-------------|
| `POST` | `/api/sessions` | Body: `projectKey`, `userId` / id fields · 세션 생성 |
| `POST` | `/api/events` | Header `x-livescreenlog-session-token` · rrweb batch |
| `POST` | `/api/heartbeat` | Keep-alive · 유지 |
| `POST` | `/api/stop` | Mark stopped · 종료 |

### Read · 조회 (network-protect · 망 보호 필요)

| Method | Path | Note · 비고 |
|--------|------|-------------|
| `GET` | `/api/sessions` | Search / filter · 검색 |
| `GET` | `/api/sessions/{id}` | Metadata · 메타 |
| `GET` | `/api/sessions/{id}/events` | `?paged=true&limit=&afterId=` |
| `GET` | `/api/sessions/{id}/live` | SSE live tail · 실시간 |

### Projects & push · 프로젝트·푸시

| Method | Path | Note · 비고 |
|--------|------|-------------|
| `*` | `/api/projects/**` | CRUD / settings · 관리 |
| `GET` | `/api/push/connect` | SDK standby SSE · 대기 |
| `GET` | `/api/push/active-terminals` | Mode B list · 접속 단말 |
| `POST` | `/api/push/trigger-record` | Mode B start · 녹화 지시 |

### Ops · 운영

| Method | Path |
|--------|------|
| `GET` | `/actuator/health` |

---

## 10. Security & privacy · 보안·개인정보

**KO**  
세션 녹화에는 DOM, 입력(마스킹 전), URL, userId 등 **개인정보·업무 정보**가 포함될 수 있습니다.  
법규·사내 정책에 맞는 보관 기간·접근 통제를 적용하세요.

**EN**  
Recordings may contain personal or business data (DOM, inputs before mask, URLs, userIds).  
Apply retention and access controls that match your legal and internal policy.

| Topic · 주제 | Guidance · 안내 |
|--------------|-----------------|
| Ingest auth | HMAC session token after create · 생성 후 HMAC |
| Dashboard | Network isolation, not public internet · 망 분리 |
| CORS | Explicit origins in prod · prod에서 Origin 명시 |
| Masking | `maskAllInputs`, block/ignore classes · 입력 마스킹 |
| Retention | `LIVESCREENLOG_RETENTION_DAYS` |
| Secrets | Env / secret manager only · 환경변수·시크릿 매니저 |

Report vulnerabilities · 취약점 제보: [SECURITY.md](SECURITY.md)

---

## 11. Operations · 운영

| Topic · 주제 | KO | EN |
|--------------|----|----|
| Schema | 기동 시 Flyway 자동 적용 | Flyway runs on startup |
| Retention job | 일 단위 오래된 세션 삭제 | Daily cleanup of old sessions |
| Rate limits | Redis 기반 생성/이벤트 제한 | Redis-backed create/event limits |
| Scaling | Mode B는 단일 인스턴스 전제 | Mode B assumes one instance |
| Backups | Postgres 볼륨 백업 필수 | Back up the Postgres volume |
| Logs | 앱·프록시 로그로 장애 추적 | Use app + proxy logs for incidents |
| Version | `version.json` + `./release.sh` | Sync versions with `release.sh` |

---

## 12. Troubleshooting · 문제 해결

| Symptom · 증상 | Check · 확인 |
|----------------|--------------|
| App won't start in prod · prod 기동 실패 | HMAC length ≥32; origins set and not `*` |
| SDK not recording · 녹화 안 됨 | API key, `id`, CORS origin, mode not NONE |
| CORS errors · CORS 오류 | Add exact page origin to `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` |
| 401 on events · 이벤트 401 | Send `x-livescreenlog-session-token` from create response |
| Empty dashboard static · 화면 비어 있음 | Run `./gradlew bootJar` so FE/SDK are packed |
| DB connection failed · DB 연결 실패 | `DB_*`, Postgres up, network |
| Live tail silent · 라이브 무응답 | Redis/Valkey up; session still ACTIVE |
| Mode B no terminals · B모드 단말 없음 | Client called init + push connect; same app instance |
| Old SessionLens clients · 구 클라이언트 | Migrate to `livescreenlog.js` / new header names |

---

## 13. Further docs · 추가 문서

| Doc · 문서 | Content · 내용 |
|------------|----------------|
| [README.md](README.md) | Project overview · 프로젝트 개요 |
| [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md) | Deploy reference · 배포 레퍼런스 |
| [docs/api/API.md](docs/api/API.md) | HTTP API detail · API 상세 |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | Architecture · 아키텍처 |
| [docs/schema/SCHEMA.md](docs/schema/SCHEMA.md) | DB schema · DB 스키마 |
| [docs/security/SECURITY.md](docs/security/SECURITY.md) | Security model · 보안 모델 |
| [docs/OPENSOURCE.md](docs/OPENSOURCE.md) | Publish checklist · 공개 체크리스트 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contributors · 기여 가이드 |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting · 취약점 제보 |

---

## License · 라이선스

**KO** · **EN**  
MIT — see [LICENSE](LICENSE).  
You may use, modify, and redistribute under the MIT terms; keep the copyright notice.
