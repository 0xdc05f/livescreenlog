# 에어갭 반입 (LiveScreenLog 0.3.1)

폐쇄망으로 이미지·산출물을 옮긴 뒤 기동하는 순서입니다. `images/`와 `files/`는 바이너리 스테이징이며 git에 넣지 않습니다.

## 기동 순서

1. USB로 `airgap` 폴더를 대상 장비에 복사합니다.
2. 앱 이미지를 로드합니다.

   ```bash
   docker load < images/livescreenlog-0.3.1.tar.gz
   ```

3. `postgres:16-alpine`과 `valkey/valkey:alpine`은 이미 있다고 가정합니다. pull하지 않습니다.
4. 환경 파일을 만들고 값을 수정합니다.

   ```bash
   cp .env.example .env
   ```

   `LIVESCREENLOG_HMAC_SECRET`(32자 이상), `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS`(`*` 금지), DB/대시보드 비밀번호를 실제 값으로 바꿉니다.
5. 스택을 올립니다.

   ```bash
   docker compose -f docker-compose.yml --env-file .env up -d
   ```

6. 브라우저에서 `http://localhost:8080/login` 으로 접속합니다. 최초 계정은 `admin` / `.env`의 부트스트랩 비밀번호입니다.
7. Nexus에 올릴 파일: `files/`의 jar, js, tgz와 `images/`의 tar입니다. tar는 docker hosted에 `docker load` 후 tag/push 합니다.
8. npm tgz는 번들러를 쓸 때만 필요합니다. 절차는 [npm-offline.md](npm-offline.md), 산출물은 `files/npm/`입니다. 런타임 SDK는 `rrweb@2.1.4`에 의존합니다.

## 이미지 tar가 없을 때

GHCR에 이미지가 없으면 온라인 장비에서 JAR로 로컬 빌드하세요.

```bash
./gradlew bootJar
docker build -f deploy/Dockerfile -t ghcr.io/0xdc05f/livescreenlog:0.3.1 .
docker save ghcr.io/0xdc05f/livescreenlog:0.3.1 | gzip > images/livescreenlog-0.3.1.tar.gz
```
