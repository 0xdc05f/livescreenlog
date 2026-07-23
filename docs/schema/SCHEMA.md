# LiveScreenLog Database Schema (PostgreSQL)

본 문서는 LiveScreenLog 백엔드에서 사용할 PostgreSQL 데이터베이스 스키마를 정의합니다.

## 1. Schema Overview

기존 s2-lite 스트림 저장소의 한계(검색 불가, 재시작 시 데이터 소실)를 극복하기 위해, 관계형 데이터베이스인 PostgreSQL을 도입합니다. 검색을 위한 메타데이터 테이블(`session_metadata`)과 실제 rrweb 이벤트를 저장하는 테이블(`session_events`)로 분리하여 성능과 유연성을 확보합니다.

## 2. Tables

### 2.1 `session_metadata`

세션의 기본 정보, 사용자 식별자, 상태 등을 저장합니다. 대시보드의 세션 목록 조회 및 검색(일자별, 사용자별, 소스별)에 주로 사용됩니다.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `session_id` | VARCHAR(255) | PRIMARY KEY | 세션의 고유 식별자 (UUID 등) |
| `project_key` | VARCHAR(255) | NOT NULL | 세션이 속한 프로젝트 키 |
| `user_id` | VARCHAR(255) | NULL | 사용자 식별자 (예: 사원번호 `EMP001`) |
| `distinct_id` | VARCHAR(255) | NULL | 사용자 고유 식별자 (보통 `user_id`와 동일하거나 기기 ID) |
| `source` | VARCHAR(255) | NULL | 유입 경로 또는 식별자 (예: 상담번호 `counsel-20260717-001`) |
| `status` | VARCHAR(50) | NOT NULL | 세션 상태 (`ACTIVE`, `STOPPED`) |
| `created_at` | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | 세션 생성 일시 |
| `updated_at` | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | 세션 마지막 업데이트(이벤트 수신) 일시 |
| `end_at` | TIMESTAMP WITH TIME ZONE | NULL | 세션 종료 일시 |

**Indexes:**
- `idx_session_metadata_created_at` on `created_at` (일자별 검색용)
- `idx_session_metadata_user_id` on `user_id` (사용자 검색용)
- `idx_session_metadata_source` on `source` (소스/상담번호 검색용)

### 2.2 `session_events`

rrweb SDK로부터 수집된 실제 이벤트 페이로드를 저장합니다. 이벤트 데이터는 구조가 가변적이므로 `JSONB` 타입을 사용하여 저장합니다.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL | PRIMARY KEY | 자동 증가 식별자 |
| `session_id` | VARCHAR(255) | NOT NULL, REFERENCES `session_metadata(session_id)` ON DELETE CASCADE | 이벤트가 속한 세션 ID |
| `timestamp` | BIGINT | NOT NULL | rrweb 이벤트 발생 타임스탬프 (밀리초) |
| `event_data` | JSONB | NOT NULL | 실제 rrweb 이벤트 객체 데이터 |
| `created_at` | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | 레코드 생성 일시 |

**Indexes:**
- `idx_session_events_session_timestamp` on `(session_id, timestamp)` (특정 세션의 이벤트를 시간순으로 빠르게 조회하기 위함)

## 2.3 `projects`

대시보드에서 관리하는 프로젝트/API 키와 녹화 정책.

| Column Name | Data Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | BIGSERIAL | PRIMARY KEY | |
| `name` | VARCHAR(255) | NOT NULL | 표시 이름 |
| `description` | TEXT | NULL | |
| `api_key` | VARCHAR(255) | NOT NULL UNIQUE | 클라이언트 projectKey |
| `recording_mode` | VARCHAR(50) | NOT NULL DEFAULT `ALL` | `ALL` / `A` / `B` / `C` / `NONE` |
| `target_users` | TEXT | NULL | Mode A 대상 userId 목록 (콤마 구분) |
| `created_at` | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | |

Flyway: `V2__projects.sql`. 스키마 단일 소스는 Flyway이며 앱 기동 시 수동 DDL은 사용하지 않습니다.

## 3. Storage Considerations

- **Batch Insert**: `JdbcTemplate.batchUpdate()` + `reWriteBatchedInserts=true`.
- **Data Retention**: `SessionTimeoutService`가 `LIVESCREENLOG_RETENTION_DAYS` 기준으로 오래된 `session_metadata` 삭제 (events CASCADE).
- **Event pagination**: `GET /api/sessions/{id}/events?paged=true&afterId=&limit=`.
- **Table Partitioning**: 대규모 트래픽 시 `session_events` 월별 파티션 검토 (후속).
