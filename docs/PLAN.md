# LiveScreenLog Spring Boot Migration Plan

본 문서는 LiveScreenLog 백엔드를 Node.js에서 Spring Boot로 전환하기 위한 전체 개발 마일스톤을 정의합니다.

## Phase 1–5: 완료

핵심 마이그레이션(Ingest / Read / Live / FE 통합 / Docker)과 Project·Recording Mode(A/B/C)·SDK 확장은 구현 완료 상태입니다.

## Phase 6: Hardening

1. Schema는 Flyway only.
2. Security: prod fail-fast, form login, project ACL, push admin 보호, CSRF, HMAC ACTIVE 검사, FORCE nonce.
3. VIEWER는 조회만. 프로젝트 배정 `roleInProject`(OWNER/ADMIN)가 뮤테이션을 제한합니다.
4. Mode B push는 Redis pub/sub fanout + presence set (멀티 노드).
5. 데모 시드 키는 `dev` 프로필만. `V11`이 기존 데모 키를 제거합니다.
6. Spring Boot 4 HTTP는 Jackson 3. 도메인 JSON은 Jackson 2 `ObjectMapper` 빈을 유지합니다.
