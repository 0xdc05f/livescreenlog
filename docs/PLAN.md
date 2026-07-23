# LiveScreenLog Spring Boot Migration Plan

본 문서는 LiveScreenLog 백엔드를 Node.js에서 Spring Boot로 전환하기 위한 전체 개발 마일스톤을 정의합니다.

## Phase 1–5: 완료

핵심 마이그레이션(Ingest / Read / Live / FE 통합 / Docker)과 Project·Recording Mode(A/B/C)·SDK 확장은 구현 완료 상태입니다. 코드가 초기 계획 문서보다 앞선 확장 기능을 포함할 수 있습니다.

## Phase 6: Hardening (진행)

프로덕션 투입 품질을 목표로 합니다.

1. **Schema single source** — Flyway only (`V1` sessions, `V2` projects). 수동 `DatabaseInitializer` 제거.
2. **Security** — prod fail-fast (HMAC/password/origins/dashboard), push admin 보호, Redis rate limit, SDK `maskAllInputs`.
3. **Runtime** — event pagination, append 시 `updated_at`, Redis publish afterCommit, retention job, SSE ping.
4. **Quality** — Testcontainers 통합 테스트, Actuator health, API 문서, compose secrets / `.env.example`.

### 운영 제약

- Mode B push emitter 맵은 **단일 인스턴스** 전제. 멀티 노드 시 sticky session 또는 Redis fanout 후속 작업 필요.
- 대시보드 Basic Auth는 망 분리/리버스 프록시 TLS와 함께 사용. SSO는 후속 범위.
