# Quick Docker Start (로컬 테스트용)

이 가이드는 **로컬에서 빠르게 테스트**하는 방법을 설명합니다.

**실제 운영 세팅**은 [PRODUCTION_GUIDE.md](PRODUCTION_GUIDE.md)를 참고하세요.

## 1. 한 번에 실행 (로컬)

```bash
git clone https://github.com/0xdc05f/livescreenlog.git
cd livescreenlog
cp .env.example .env
# .env에서 LIVESCREENLOG_HMAC_SECRET과 ALLOWED_CAPTURE_ORIGINS만 수정
docker compose -f deploy/docker-compose.yml --env-file .env up -d
```

## 2. 접속

- Dashboard: `http://localhost:8080/`
- Health: `http://localhost:8080/actuator/health`

## 3. 로그 / 중지

```bash
docker compose -f deploy/docker-compose.yml logs -f app
docker compose -f deploy/docker-compose.yml down
```

## 4. Docker 이미지

공식 이미지는 GHCR에 있습니다:

```bash
docker pull ghcr.io/0xdc05f/livescreenlog:0.3.0
docker pull ghcr.io/0xdc05f/livescreenlog:latest
```

운영 환경에서는 **Vultr PostgreSQL + Docker Valkey + GHCR 앱 이미지** 조합을 가장 추천합니다.  
전체 운영 가이드는 [PRODUCTION_GUIDE.md](PRODUCTION_GUIDE.md)를 보세요.
