# Production Setup Guide (실서비스 최소 세팅)

이 가이드는 **번잡한 과정 없이** 실제 서비스를 세팅하는 방법을 설명합니다.

**기본 철학**: 
- Postgres는 Vultr에서 받는다
- Valkey는 Docker로 간단히 띄운다
- LiveScreenLog는 GHCR 이미지로 바로 실행한다
- 대시보드에서 프로젝트 만들고 API Key만 복사하면 끝

## 1. 전체 흐름 (5단계로 끝)

1. **Vultr**에서 PostgreSQL 생성
2. Docker로 **Valkey** 실행
3. GHCR 이미지로 **LiveScreenLog** 실행
4. 브라우저로 대시보드 들어가서 프로젝트 생성 → **API Key** 복사
5. 클라이언트 코드에 `apiKey` + `dsn` 넣기

이게 전부입니다.

## 2. 상세 (최대한 단순하게)

### 2.1 PostgreSQL (Vultr)

Vultr → Databases → PostgreSQL 생성 후 아래 정보를 메모:
- Host
- Port (보통 5432)
- Database name
- User
- Password

### 2.2 Valkey (가장 간단한 방법)

```bash
docker run -d --name valkey -p 6379:6379 valkey/valkey:alpine
```

(운영에서도 이 정도로 충분한 경우가 많습니다.)

### 2.3 LiveScreenLog 실행 (GHCR 이미지)

**반드시 넣어야 하는 값** (두 개):
- `LIVESCREENLOG_HMAC_SECRET` — 32자 이상 강력한 랜덤 문자열
- `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` — 실제 사이트 Origin (예: `https://your-site.com`)

**실행 명령**:

```bash
docker run -d \
  --name livescreenlog \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=your-vultr-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=livescreenlog \
  -e DB_USER=postgres \
  -e DB_PASSWORD=your-strong-password \
  -e REDIS_HOST=localhost \
  -e REDIS_PORT=6379 \
  -e LIVESCREENLOG_HMAC_SECRET='여기에_32자_이상_진짜_랜덤_문자열_넣으세요' \
  -e LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS='https://your-site.com,https://admin.your-site.com' \
  ghcr.io/0xdc05f/livescreenlog:0.3.0
```

이미지:
- `ghcr.io/0xdc05f/livescreenlog:0.3.0` (권장)
- `ghcr.io/0xdc05f/livescreenlog:latest`

### 2.4 프로젝트 생성 및 API Key 발급

1. `http://your-server:8080/` 으로 접속
2. **설정 → 프로젝트 관리** 들어가기
3. 새 프로젝트 만들기
4. 생성된 **API Key** 전체 복사

### 2.5 클라이언트에 적용

**가장 흔한 방식 (npm)**:

```bash
npm install livescreenlog
```

```js
import { LiveScreenLog } from 'livescreenlog';

LiveScreenLog.init({
  apiKey: '여기에_복사한_API_KEY',
  dsn: 'https://your-livescreenlog-host',
  id: currentUser.id,     // 필수
});
```

**CDN 방식** (번들러 없이):

```html
<script src="https://cdn.jsdelivr.net/npm/livescreenlog@0.3.0/dist/livescreenlog.js"></script>
<script>
  LiveScreenLog.init({
    apiKey: '여기에_복사한_API_KEY',
    dsn: 'https://your-livescreenlog-host',
    id: 'user-123'
  });
</script>
```

서버에서 직접 서빙하고 싶으면 `/livescreenlog.js`를 사용하세요.

## 3. 접속 확인

- Dashboard: `http://your-host:8080/`
- Health: `http://your-host:8080/actuator/health`

## 4. 한 줄 요약

Vultr Postgres + Docker Valkey + `docker run ghcr.io/0xdc05f/livescreenlog` + 대시보드에서 키 하나 복사 → 클라이언트에 넣기.

이게 끝입니다.

**CDN (번들러 없이)**:
```html
<script src="https://cdn.jsdelivr.net/npm/livescreenlog@0.3.0/dist/livescreenlog.js"></script>
<script>
  LiveScreenLog.init({
    apiKey: '여기에_발급받은_API_KEY',
    dsn: 'https://your-livescreenlog-host',
    id: 'user-001'
  });
</script>
```

**서버에서 직접 서빙하는 경우** (가장 안정적):
```html
<script src="https://your-livescreenlog-host/livescreenlog.js"></script>
```

## 4. 한 번에 실행하고 싶을 때 (로컬 테스트용 compose)

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
# .env에서 HMAC_SECRET과 ALLOWED_CAPTURE_ORIGINS만 채우기
docker compose -f deploy/docker-compose.yml --env-file .env up -d
```

## 5. 보안 체크리스트 (반드시)

- [ ] `LIVESCREENLOG_HMAC_SECRET`는 32자 이상 강력한 값 (약한 값이면 prod에서 기동 거부)
- [ ] `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS`에 `*` 사용 금지
- [ ] Postgres/Redis는 외부에서 직접 접근 불가능하게 (방화벽/사설망)
- [ ] 앱 앞단에 TLS 적용 (Caddy, nginx, Cloud LB 등)
- [ ] Dashboard form-login (SUPER_ADMIN/ADMIN) + /api/admin/** SUPER_ADMIN only. VPN/사설망/리버스프록시 + TLS 권장 (see SECURITY.md bootstrap)

## 6. 이미지 버전

- `ghcr.io/0xdc05f/livescreenlog:latest`
- `ghcr.io/0xdc05f/livescreenlog:0.3.0`

릴리스 태그를 기준으로 이미지가 생성됩니다.
