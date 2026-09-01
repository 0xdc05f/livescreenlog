# LiveScreenLog 매뉴얼

> Repo 버전: `version.json` 기준 · 라이선스: MIT  
> 저장소: https://github.com/0xdc05f/livescreenlog

### 다운로드

빌드 없이 쓰려면 GitHub Releases에서 받으세요. 베타 태그(`v0.2.0` 등) 이후 링크가 활성화됩니다.

| 산출물 | 최신 | 설명 |
|--------|------|------|
| **Server JAR** | [livescreenlog.jar](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar) | JRE 21 + PostgreSQL + Valkey/Redis |
| **Browser JS** | [livescreenlog.js](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js) | UMD · `window.LiveScreenLog` |
| **전체 릴리스** | [Releases 페이지](https://github.com/0xdc05f/livescreenlog/releases) | 버전별 JAR/JS + `SHA256SUMS.txt` |
| **npm SDK** (선택) | [`livescreenlog`](https://www.npmjs.com/package/livescreenlog) | rrweb이 내부에 번들됨. 또는 `/livescreenlog.js` 사용 |

```bash
# 운영자용 — JAR 다운로드 후 실행 (DB/Redis/환경변수 설정 후)
curl -fsSL -o livescreenlog.jar \
  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar
java -jar livescreenlog.jar
```

```html
<!-- 연동용 — 서버의 /livescreenlog.js를 사용하거나 릴리스 URL 고정 -->
<script src="https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js"></script>
```

**프로덕션 연동은 가급적 자체 서버의 `/livescreenlog.js` 또는 버전 고정 Release URL을 쓰세요.**

릴리스 절차 (유지보수자용): [docs/release/RELEASE.md](docs/release/RELEASE.md)

---

## 목차

1. [개요](#1-개요)
2. [요구 사항](#2-요구-사항)
3. [빠른 시작](#3-빠른-시작)
4. [설정](#4-설정)
5. [배포](#5-배포)
6. [관리 화면](#6-관리-화면)
7. [SDK 연동](#7-sdk-연동)
8. [녹화 모드](#8-녹화-모드)
9. [HTTP API 요약](#9-http-api-요약)
10. [보안·개인정보](#10-보안개인정보)
11. [운영](#11-운영)
12. [문제 해결](#12-문제-해결)
13. [추가 문서](#13-추가-문서)

---

## 1. 개요

LiveScreenLog는 브라우저 화면 세션을 수집·저장·재생하는 **셀프호스팅** 서버입니다.  
rrweb으로 DOM 변화를 캡처하고, PostgreSQL에 저장하며, Valkey/Redis로 라이브 테일링을 지원합니다.  
대시보드(Svelte)와 브라우저 SDK(`livescreenlog` / `/livescreenlog.js`)가 포함됩니다. (npm 패키지에 rrweb이 함께 번들됩니다.)

| 구성 | 역할 |
|------|------|
| App (JRE 21 / Spring Boot) | API, 대시보드, 정적 SDK |
| PostgreSQL 16+ | 세션 메타데이터 및 이벤트 (Flyway) |
| Valkey 또는 Redis | Pub/Sub 라이브 테일, 레이트 리밋 |
| Browser SDK | 캡처 + 푸시 (Mode B) 클라이언트 |

---

## 2. 요구 사항

**런타임 (서버에 설치)**
- JRE 21 이상 (실행만 할 때; 빌드 시 JDK 21)
- PostgreSQL 16+
- Valkey 또는 Redis
- (선택) Docker, 리버스 프록시(TLS)

**빌드 머신에만 필요**
- Node.js 20+ (프론트/SDK 재빌드)
- Gradle Wrapper (`./gradlew`)

---

## 3. 빠른 시작

### 3.0 빌드된 JAR로 실행 (운영자용)

소스를 빌드하지 않을 때. JRE 21, Postgres, Valkey/Redis, 환경변수 필요.

```bash
curl -fsSL -o livescreenlog.jar \
  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar
# SPRING_PROFILES_ACTIVE=prod 및 시크릿 설정 — §4 / docs/deploy/DEPLOY.md 참조
java -jar livescreenlog.jar
```

| 다운로드 | URL |
|----------|-----|
| Latest JAR | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar |
| Latest JS | https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js |
| Releases | https://github.com/0xdc05f/livescreenlog/releases |

### 3.1 로컬 개발

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
docker compose -f deploy/docker-compose.yml up -d postgres valkey
./gradlew bootRun
```

| 확인 | URL |
|------|-----|
| Health | http://localhost:8080/actuator/health |
| 대시보드 | http://localhost:8080/ |

대시보드 정적 자산이 비어 있으면 `./gradlew bootJar` 또는 `copySdkToStatic` 후 실행하세요.

### 3.2 전체 스택 예시 (참고용 compose)

```bash
cp .env.example .env
# LIVESCREENLOG_HMAC_SECRET (32자 이상)과 LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS 수정
./gradlew bootJar
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

`deploy/` 아래 파일은 **참고용**입니다. 환경에 맞게 네트워크·시크릿·TLS를 수정하세요.  
자세한 배포: [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md)

---

## 4. 설정

`.env.example`을 복사해 `.env`를 만듭니다. **실키 `.env`는 커밋하지 마세요.**  
`prod` 프로필은 약한 HMAC·와일드카드 origin(`*`)이면 **기동을 거부**합니다.

| 변수 | prod 필수 | 설명 |
|------|-----------|------|
| `LIVESCREENLOG_HMAC_SECRET` | 예 | 세션 토큰 서명 비밀키 (≥32자) |
| `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` | 예 | CORS 허용 Origin 목록 (`,` 구분, `*` 금지) |
| `LIVESCREENLOG_PROJECT_KEY` | 아니오 | 전역 폴백 프로젝트 키 (선택) |
| `LIVESCREENLOG_RETENTION_DAYS` | 아니오 | 자동 삭제 일수 (기본 30, `0`이면 비활성화) |
| `LIVESCREENLOG_RATE_LIMIT_CREATE` | 아니오 | 분당 세션 생성 한도 |
| `LIVESCREENLOG_RATE_LIMIT_EVENTS` | 아니오 | 분당 이벤트 전송 한도 |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | prod 시 필수 | PostgreSQL |
| `REDIS_HOST` `REDIS_PORT` `REDIS_PASSWORD` | 호스트/포트 필수 | Valkey/Redis |

YAML 접두사: `livescreenlog.*`  
파일: `src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`

---

## 5. 배포

### 5.1 권장 구성

```
[인터넷]
    → 리버스 프록시 (TLS)
        → 앱 :8080  (java -jar …)
    PostgreSQL   (비공개망)
    Valkey/Redis (비공개망)
```

대상 서버에는 **JRE 21 + jar + Postgres + Valkey/Redis**면 충분합니다.  
JDK·Node·Gradle은 빌드 머신/CI에만 있으면 됩니다.

### 5.2 Bare metal (jar 직접 실행)

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=... DB_USER=... DB_PASSWORD=...
export REDIS_HOST=...
export LIVESCREENLOG_HMAC_SECRET='…'   # 32자 이상
export LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://app.example.com,https://lsl.example.com'
java -jar build/libs/*.jar
```

### 5.3 Docker 이미지

```bash
./gradlew bootJar
docker build -f deploy/Dockerfile -t livescreenlog:local .
```

Dockerfile은 **미리 빌드된 jar**를 복사하는 얇은 런타임 이미지입니다.

### 5.4 베타 체크리스트

- [ ] 강한 고유 `LIVESCREENLOG_HMAC_SECRET`
- [ ] 실제 캡처 Origin ( `*` 금지 )
- [ ] DB/Redis 공개 노출 금지
- [ ] 앱 앞단 TLS
- [ ] Postgres 디스크 및 백업
- [ ] 보관 기간 정책 반영
- [ ] 조회/관리 API 망 분리

---

## 6. 관리 화면

브라우저에서 서버 origin(`http://host:8080/`)으로 접속합니다.  
상단 언어 선택(한국어 / English)으로 UI 문구를 바꿀 수 있습니다.

| 영역 | 설명 |
|------|------|
| 리플레이 | 세션 목록·검색·재생. 실시간 필터(최근 5분 heartbeat). 라이브 진행바는 벽시계 기준. 추천 칩은 에러 세션만 표시 |
| 설정 | 프로젝트, 연동 가이드, 서버 설정, 사용자, 내 계정(비밀번호 변경), **통계** |
| 상단 | 로고 클릭=리플레이, 언어, 비밀번호 변경, 로그아웃 |

URL 예: `/?view=settings&tab=stats`. 브라우저 뒤로가기는 설정↔리플레이 전환입니다. 로그인 페이지는 히스토리에 남지 않습니다 (`location.replace`).

**인증**

- 기본 개발 계정: `admin` / `admin-password-need-to-change` (`users` 테이블이 비어 있을 때 부트스트랩). 설정 → 내 계정에서 변경하세요.
- `POST /api/sessions` 는 permitAll (projectKey는 서버에서 검증). GET 세션·대시보드는 ADMIN.
- HMAC은 `/api/events`, `/api/heartbeat`, `/api/stop`만 적용되며 **요청 스코프**입니다 (대시보드 JSESSIONID를 덮지 않음). SDK fetch는 `credentials: 'omit'`.
- 대시보드 쿠키: 8시간, HttpOnly, SameSite=Lax.
- 녹화 유휴: heartbeat가 30분 없으면 STOPPED. HMAC 토큰 유효 24시간.
- `/api/push/**` 는 공개 (projectKey 검증). 운영 시 주의.
- 상세: [docs/security/SECURITY.md](docs/security/SECURITY.md)

---

## 7. SDK 연동

### 7.1 프로젝트 생성

대시보드 → 설정 → 프로젝트 관리에서 프로젝트를 만들고 **API Key**를 복사합니다.

### 7.2 스크립트 태그

**권장:** LiveScreenLog 서버에서 직접 서빙 (API와 동일 Origin).  
**대안:** [Release JS](https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js) (프로덕션에서는 버전 고정)

```html
<script src="https://YOUR-LSL-HOST/livescreenlog.js"></script>
<!-- 또는: https://github.com/0xdc05f/livescreenlog/releases/download/v0.2.0/livescreenlog-0.2.0.js -->
<script>
  LiveScreenLog.init({
    apiKey: 'YOUR_PROJECT_API_KEY',
    dsn: 'https://YOUR-LSL-HOST',
    id: 'user-001',
    onSessionReady: function (sessionId) {},
    onInitError: function (err) {},
    onStandby: function (mode) {}  // Mode B/C 대기
  });
</script>
```
Note: 동적 CDN fallback은 `rrweb@2.1.1`. 번들 SDK는 rrweb 2.1.1을 포함합니다.

### 7.3 npm / ESM

```bash
npm i livescreenlog
# rrweb은 의존성으로 포함되어 있으므로 별도 설치 불필요
```

```js
import { LiveScreenLog } from 'livescreenlog';

LiveScreenLog.init({
  apiKey: 'YOUR_PROJECT_API_KEY',
  dsn: 'https://YOUR-LSL-HOST',
  id: 'user-001'
});
```

### 7.4 중요 식별자

| 항목 | 값 |
|------|----|
| 전역 객체 | `window.LiveScreenLog` |
| 번들 URL | `/livescreenlog.js` |
| 인증 헤더 | `x-livescreenlog-session-token` |
| 마스킹 차단 클래스 | `livescreenlog-block` |
| 마스킹 무시 클래스 | `livescreenlog-ignore` |

민감 UI에 `class="livescreenlog-block"`을 주면 캡처에서 가려집니다.  
SDK 기본값은 `maskAllInputs: true`입니다.

### 7.5 로컬 데모

서버에서 `/sample-a.html`(지정 사용자), `/sample-b.html`(원격 트리거), `/sample-c.html`(에러), `/sample-all.html`(전체 수집). 루트 복사본은 gitignore일 수 있으며, 있으면 빌드 시 static으로 복사됩니다.

---

## 8. 녹화 모드

| 모드 | 설명 |
|------|------|
| **ALL** | 모든 사용자 녹화 |
| **NONE** | 녹화 안 함 |
| **A** | 대상 userId 목록만 |
| **B** | 관리자가 접속 단말에 녹화 지시 |
| **C** | `error` / `unhandledrejection` 시 시작 |

**Mode B 제약**  
푸시 연결 목록은 **프로세스 로컬**입니다. 베타/단일 인스턴스를 전제로 하세요.  
스케일 아웃 시 sticky session 또는 별도 fan-out이 필요합니다.

---

## 9. HTTP API 요약

Base URL = 서버 origin (예: `https://lsl.example.com`).  
상세: [docs/api/API.md](docs/api/API.md)

### 수집 (생성 후 HMAC)

| Method | Path | 비고 |
|--------|------|------|
| `POST` | `/api/sessions` | Body: `projectKey`, `userId` / id 필드 (permitAll; projectKey 서버 검증) |
| `POST` | `/api/events` | Header `x-livescreenlog-session-token` · rrweb 배치 |
| `POST` | `/api/heartbeat` | 유지 |
| `POST` | `/api/stop` | 종료 |

### 조회 (망 보호 필요)

| Method | Path | 비고 |
|--------|------|------|
| `GET` | `/api/sessions` | `status`, `updatedAfter` 등 (실시간 필터) |
| `GET` | `/api/sessions/live` | 목록 SSE |
| `GET` | `/api/sessions/recommended?limit=` | 에러 추천 |
| `GET` | `/api/sessions/{id}` | 메타데이터 |
| `GET` | `/api/sessions/{id}/events` | 대시보드는 기본 `paged=true` |
| `GET` | `/api/sessions/{id}/live` | SSE 라이브 테일 |
| `GET` | `/api/stats/overview?projectKey=` | 사용 통계 |
| `GET` | `/api/me` | 현재 사용자 |
| `POST` | `/api/me/password` | 비밀번호 변경 |

### 프로젝트·푸시

| Method | Path | 비고 |
|--------|------|------|
| `*` | `/api/projects/**` | CRUD / 설정 |
| `GET` | `/api/push/connect` | SDK 대기 SSE |
| `GET` | `/api/push/active-terminals` | Mode B 접속 단말 목록 |
| `POST` | `/api/push/trigger-record` | Mode B 녹화 지시 |

### 운영

| Method | Path |
|--------|------|
| `GET` | `/actuator/health` |

---

## 10. 보안·개인정보

세션 녹화에는 DOM, 입력(마스킹 전), URL, userId 등 **개인정보·업무 정보**가 포함될 수 있습니다.  
법규·사내 정책에 맞는 보관 기간·접근 통제를 적용하세요.

| 주제 | 안내 |
|------|------|
| Ingest 인증 | 생성 후 HMAC 세션 토큰 |
| 대시보드 | 망 분리 (공개 인터넷 노출 금지) |
| CORS | prod에서는 명시적 Origin만 허용 |
| 마스킹 | `maskAllInputs`, block/ignore 클래스 |
| 보관 기간 | `LIVESCREENLOG_RETENTION_DAYS` |
| 시크릿 | 환경변수 / 시크릿 매니저만 사용 |
| HMAC 스코프 | 필터가 이전 SecurityContext를 복원 (대시보드 세션 유지) |
| 로그인 | fetch + `location.replace('/')` — Back이 로그인으로 안 돌아감 |
| 비밀번호 | 설정 → 내 계정 / `POST /api/me/password` |

취약점 제보: [SECURITY.md](SECURITY.md)

---

## 11. 운영

| 주제 | 내용 |
|------|------|
| 스키마 | 기동 시 Flyway 자동 적용 |
| 보관 작업 | 일 단위 오래된 세션 삭제 |
| 레이트 리밋 | Redis 기반 생성/이벤트 제한 |
| 스케일링 | Mode B는 단일 인스턴스 전제 |
| 백업 | Postgres 볼륨 백업 필수 |
| 로그 | 앱·프록시 로그로 장애 추적 |
| 버전 | `version.json` + `./release.sh` |

---

## 12. 문제 해결

| 증상 | 확인 |
|------|------|
| prod에서 앱이 시작되지 않음 | HMAC 길이 ≥32, Origin 설정 및 `*` 아님 |
| SDK가 녹화하지 않음 | API 키, `id`, CORS Origin, mode가 NONE이 아님 |
| 샘플 녹화 차단 | 프로젝트 키/targetUsers, 시크릿+하드 리프레시. 연결 실패 vs 정책 차단 구분 |
| bootRun 8080 in use | `lsof -i :8080` 후 해당 PID kill |
| `/?continue` 흰 화면 | 요청 캐시 비활성, `/` → index.html |
| 뒤로가기가 로그아웃처럼 보임 | 로그인 히스토리 replace. 설정에서 뒤로가면 리플레이 |
| 실시간 목록에 없음 | 5분 내 updatedAt, heartbeat가 DB에 반영되는지 |
| 추천 레일 빈 화면 | 에러 세션이 없으면 레일을 숨기는 것이 정상 |
| CORS 오류 | `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS`에 정확한 페이지 Origin 추가 |
| 이벤트 401 | create 응답의 `x-livescreenlog-session-token` 전송 |
| 대시보드 정적 자산 비어 있음 | `./gradlew bootJar` 실행 (FE/SDK 포함) |
| DB 연결 실패 | `DB_*`, Postgres 실행 중, 네트워크 |
| 라이브 테일 무응답 | Redis/Valkey 실행 중, 세션 ACTIVE 상태 |
| Mode B 단말 없음 | 클라이언트가 init + push connect 호출, 동일 앱 인스턴스 |
| 구 SessionLens 클라이언트 | `livescreenlog.js` / 새 헤더명으로 마이그레이션 |

---

## 13. 추가 문서

| 문서 | 내용 |
|------|------|
| [README.md](README.md) | 프로젝트 개요 |
| [GitHub Releases](https://github.com/0xdc05f/livescreenlog/releases) | JAR / JS 다운로드 |
| [docs/release/RELEASE.md](docs/release/RELEASE.md) | 릴리스 절차 |
| [docs/deploy/DEPLOY.md](docs/deploy/DEPLOY.md) | 배포 레퍼런스 |
| [docs/api/API.md](docs/api/API.md) | HTTP API 상세 |
| [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) | 아키텍처 |
| [docs/schema/SCHEMA.md](docs/schema/SCHEMA.md) | DB 스키마 |
| [docs/security/SECURITY.md](docs/security/SECURITY.md) | 보안 모델 |
| [docs/OPENSOURCE.md](docs/OPENSOURCE.md) | 공개 체크리스트 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 기여 가이드 |
| [SECURITY.md](SECURITY.md) | 취약점 제보 |

---

## 라이선스

MIT — [LICENSE](LICENSE) 참조.  
MIT 조건 하에 사용·수정·재배포 가능. 저작권 고지 유지.