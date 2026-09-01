# LiveScreenLog Spring Boot Migration Plan

본 문서는 LiveScreenLog 백엔드를 Node.js에서 Spring Boot로 전환하기 위한 전체 개발 마일스톤을 정의합니다.

## Phase 1–5: 완료

핵심 마이그레이션(Ingest / Read / Live / FE 통합 / Docker)과 Project·Recording Mode(A/B/C)·SDK 확장은 구현 완료 상태입니다. 코드가 초기 계획 문서보다 앞선 확장 기능을 포함할 수 있습니다.

## Phase 6: Hardening (진행)

프로덕션 투입 품질을 목표로 합니다. (auth scoping + admin foundation 진행 중)

1. **Schema single source** — Flyway only (`V1` sessions, `V2` projects). 수동 `DatabaseInitializer` 제거.
2. **Security** — prod fail-fast (HMAC/password/origins/dashboard), form login + bootstrap, project scoping via user_projects, push admin 보호, Redis rate limit, SDK `maskAllInputs`.
3. **Runtime** — event pagination, append 시 `updated_at`, Redis publish afterCommit, retention job, SSE ping.
4. **Quality** — Testcontainers 통합 테스트, Actuator health, API 문서, compose secrets / `.env.example`.
5. **Auth Scoping** — UserProjectAccessService extracted; SessionReadService + ProjectService respect allowed project keys for non-SUPER_ADMIN.
6. **Admin Backend** — minimal AdminUserController (list/create) + DTOs; /api/admin/** requires SUPER_ADMIN.
- Wave 1: CLI additional admin via --create-admin in bootstrap; basic audit log (V8, service, /api/admin/audit).

### 운영 제약

- Mode B push emitter 맵은 **단일 인스턴스** 전제. 멀티 노드 시 sticky session 또는 Redis fanout 후속 작업 필요.
- 대시보드 form login + roles; full per-project assignment UI and VIEWER role enforcement in progress.
- See tasks/phase-6/TASK.md and docs/security/SECURITY.md for bootstrap details.
- [x] API key rotation / revocation UI per project (added rotate-key endpoint + UI)

13. **Robust offline buffering + retry in SDK** — IMPLEMENTED (in sdk/src/index.ts: pendingQueue + localStorage persist/restore 'livescreenlog-pending', retry backoff, online listener, re-enqueue on send fail, trim MAX=2000, updated flush/sendEvents in begin+emits). Verified no break to init/record paths. Build: use `cd sdk && npm run build` (manual run after).
