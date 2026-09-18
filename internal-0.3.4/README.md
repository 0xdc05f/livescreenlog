# LiveScreenLog 0.3.4 개발계

공유 compose의 postgres / redis 를 사용한다. 이 폴더의 `docker-compose.yml` 은 단독 스택이 아니라 `/app/docker-compose.yml` 에 붙일 서비스 샘플이다.

## 서버 레이아웃

```
/app/docker-compose.yml              # 다른 앱 + postgres + redis + livescreenlog
/data/livescreenlog/env/env.dev      # 환경값 (여기만)
/data/livescreenlog/logs/            # 앱 로그 바인드
```

## 파일 구분

| 파일 | 종류 | docker load |
|------|------|-------------|
| `docker-images/livescreenlog-0.3.4-image.tar.gz` | Docker 이미지 | **이 파일만** |
| `images/livescreenlog-0.3.4.tar.gz` | 동일 이미지 | 가능 |
| `files/livescreenlog-0.3.4.tgz` | npm 패키지 | **하면 안 됨** |
| `files/npm/*.tgz` | npm 패키지 | **하면 안 됨** |

`.tgz` 는 npm 이다. `docker load` 는 `*-image.tar.gz` 만.

## 이미지 아키텍처

이미지 아키텍처 linux/amd64 (내부 Linux 서버용). Mac에서 이 tar를 load 하면 qemu/rosetta 필요할 수 있음.

## 준비

```bash
mkdir -p /data/livescreenlog/env /data/livescreenlog/logs
cp env.example /data/livescreenlog/env/env.dev
chmod 600 /data/livescreenlog/env/env.dev
```

`env.dev` 에서 DB/대시보드 비밀번호, `LIVESCREENLOG_SECURITY_HMAC_SECRET`(32자 이상) 을 실제 값으로 바꾼다.

CORS: `LIVESCREENLOG_SECURITY_ALLOWED_CAPTURE_ORIGINS` 에 브라우저 origin 만 콤마로. prod 에서 `*` 금지.

호스트가 `postgres` / `redis` 가 아니면 서비스명으로 맞춘다. redis 가 `valkey` 이면 `REDIS_HOST=valkey` 이고 compose `depends_on` 도 `valkey`.

로그 디렉터리에 컨테이너 사용자(non-root `lsl`, 보통 uid 100) 쓰기 권한:

```bash
docker run --rm --entrypoint id livescreenlog:0.3.4
chown -R 100:100 /data/livescreenlog/logs
```

## 이미지 로드 후 기동

```bash
docker load -i docker-images/livescreenlog-0.3.4-image.tar.gz
docker tag ghcr.io/0xdc05f/livescreenlog:0.3.4 livescreenlog:0.3.4
```

`livescreenlog` 서비스 블록을 `/app/docker-compose.yml` 에 합친 뒤:

```bash
docker compose -f /app/docker-compose.yml up -d livescreenlog
```

`env_file` 이 컨테이너에 주입하므로 `--env-file` 불필요. compose `environment` 에는 `SPRING_PROFILES_ACTIVE=prod` 만 두고 나머지 키는 `env.dev` 와 중복하지 않는다.

접속: `http://<host>:8080/login`

## 최초 admin

`users` 테이블이 **비어 있을 때만** SUPER_ADMIN 이 생성된다. 비밀번호는 `docker logs` 의 `FIRST BOOT` 배너에 **한 번만** 출력된다. 이후 env 대시보드 비밀번호는 무시.

## Vue SDK — id 지연

로그인 전에 id 없이 init 하고, 로그인 후 `setUser` 한다.

```js
import { LiveScreenLog } from 'livescreenlog';

LiveScreenLog.init({ dsn, apiKey, integration: 'vue' });
LiveScreenLog.setUser(user.id);
LiveScreenLog.setTag('dept', user.dept);
LiveScreenLog.setTags({ app: 'erp' });
```

## 설정 시 자주 막히는 것

- hmac / origins / dashboard 는 `LIVESCREENLOG_SECURITY_*` (`LIVESCREENLOG_SECURITY_HMAC_SECRET`, `LIVESCREENLOG_SECURITY_ALLOWED_CAPTURE_ORIGINS`, `LIVESCREENLOG_SECURITY_DASHBOARD_*`). `SECURITY_` 없는 동일 이름은 기동 실패.
- HTTP 개발계는 `COOKIE_SECURE=false` (또는 `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`). HTTPS 뒤에 둘 때만 true.
- 앱 포트는 `8090:8080` (톰캣 8080). `8090:8090`이면 `SERVER_PORT=8090`. CORS origin도 브라우저 포트.
- compose 서비스명 사용 (`shared-db`, `valkey`). 앱과 DB는 같은 `networks:` (예: devops-net).
- 컨테이너 간 DB 포트는 **5432**. 호스트 매핑 15432는 쓰지 않음.
- 최초 SUPER_ADMIN 비밀번호는 `docker logs` 의 `FIRST BOOT` 배너에 한 번만 출력. 이후 env 비밀번호는 무시.
- 이미지는 linux/amd64. Mac arm64 tar 아님. `exec format error` 나면 아키텍처 확인.
- `.tgz` 는 npm. `docker load` 는 `*-image.tar.gz` 만.

## 로그

호스트: `/data/livescreenlog/logs/livescreenlog.log`  
stdout: `docker logs livescreenlog-app`

## npm-hosted

SDK 패키지는 `files/npm` 에 pack 한다. 모든 `.tgz` 를 npm-hosted 에 publish 한 뒤:

```bash
npm i livescreenlog@0.3.4 --registry https://nexus.example/repository/npm-hosted/
```
