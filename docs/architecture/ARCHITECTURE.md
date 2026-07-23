# LiveScreenLog Spring Boot Architecture

## 1. System Overview

본 문서는 LiveScreenLog 백엔드를 Spring Boot 기반으로 전환하기 위한 아키텍처를 정의합니다. 기존 s2-lite 스트림 저장소를 PostgreSQL과 Redis로 대체하여 데이터 영속성, 복합 검색, 그리고 실시간 라이브 테일링 기능을 제공합니다.

## 2. Component Architecture

시스템은 크게 브라우저(rrweb SDK), Spring Boot 백엔드 서버, PostgreSQL(데이터 저장소), Redis(메시지 브로커)로 구성됩니다.

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Client (SDK)** | rrweb, JavaScript | 웹 브라우저에서 사용자 상호작용(DOM 변화, 마우스 이동 등)을 캡처하여 백엔드로 전송합니다. |
| **Backend Server** | Java 21, Spring Boot 3.x | 수집된 이벤트를 처리하고, 데이터를 저장하며, 대시보드에 API를 제공합니다. Virtual Threads를 활용하여 동시성 처리 성능을 극대화합니다. |
| **Database** | PostgreSQL 16+ | 세션 메타데이터와 rrweb 이벤트 페이로드(JSONB)를 영구적으로 저장합니다. 일자별, 사용자별 검색을 지원합니다. |
| **Message Broker** | Redis | 라이브 테일링 기능을 위해 Pub/Sub 메커니즘을 제공합니다. 수신된 이벤트를 실시간으로 구독자(대시보드)에게 브로드캐스트합니다. |

## 3. Data Flow

### 3.1 Event Ingestion Flow (데이터 수집)

1. 브라우저 SDK가 `POST /api/events` 엔드포인트로 rrweb 이벤트 배치를 전송합니다.
2. Spring Security 커스텀 필터가 요청의 HMAC 토큰(`x-livescreenlog-session-token`)을 검증합니다.
3. Controller가 요청을 받아 Service 레이어로 전달합니다.
4. Service 레이어는:
   - **DB Storage**: `JdbcTemplate.batchUpdate()`로 `session_events` 벌크 인서트 + `session_metadata.updated_at` 갱신.
   - **Live Broadcast**: 트랜잭션 **커밋 후** Redis Pub/Sub `session:live:{session_id}`로 Publish.

### 3.2 Live Tailing Flow (실시간 재생)

1. 관리자가 대시보드에서 특정 세션의 라이브 뷰를 요청하면 `GET /api/sessions/{id}/live` 엔드포인트를 호출합니다.
2. Controller는 Spring WebMVC의 `SseEmitter` 객체를 생성하여 반환하고, 연결을 유지합니다.
3. 서버는 Redis의 `session:live:{session_id}` 채널을 Subscribe 합니다.
4. Redis 채널에 새로운 메시지(이벤트)가 Publish 되면, 서버는 열려있는 `SseEmitter`를 통해 클라이언트(대시보드)로 이벤트를 푸시합니다.

### 3.3 Session Search Flow (과거 세션 검색)

1. 대시보드에서 날짜 범위, 사용자 ID, 상담번호 등의 조건으로 `GET /api/sessions` 엔드포인트를 호출합니다.
2. Service 레이어는 JPA Criteria API 또는 QueryDSL을 사용하여 PostgreSQL `session_metadata` 테이블에 동적 쿼리를 실행합니다.
3. 검색된 세션 목록을 반환합니다.

## 4. Concurrency Model

Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`)로 I/O 바운드 작업(DB, Redis, SSE)을 처리합니다.

## 5. Mode B Push Signaling

`/api/push/connect` 대기 연결은 프로세스 로컬 emitter 맵에 보관됩니다. 라이브 테일링(Redis Pub/Sub)과 달리 **단일 인스턴스** 전제입니다. 스케일 아웃 시 sticky session 또는 Redis fanout이 필요합니다.

## 6. Schema

PostgreSQL 스키마는 Flyway만 사용합니다 (`V1__init_schema.sql`, `V2__projects.sql`).
