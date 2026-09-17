# Security Scan Report

> Snapshot of a local scan. Not a live CI badge. Re-run tools before release.

**Date:** 2026-09-16

## What was scanned

| Target | Tool | Scope |
|--------|------|-------|
| `frontend/` | npm audit | current lockfile |
| `sdk/` | npm audit | current lockfile |
| `frontend/` | Snyk Open Source | npm, including dev |
| `sdk/` | Snyk Open Source | npm, including dev |
| Repository | Snyk Open Source | Maven (Spring Boot BOM) |
| Repository | Snyk Code | SAST |

## npm audit

| Target | Critical | High | Moderate | Low | Total |
|--------|----------|------|----------|-----|------:|
| `frontend/` | 0 | 0 | 0 | 0 | **0** |
| `sdk/` | 0 | 0 | 0 | 0 | **0** |

## Snyk Open Source (this tree)

| Target | Result |
|--------|--------|
| `frontend/` | 0 issues (`rrweb@2.1.4`, `postcss@8.5.28`, `nanoid@3.3.19`) |
| `sdk/` | 0 issues |
| Maven / Spring Boot 4.1.1 | Jackson CVEs on BOM 2.21.5 / 3.1.5 — **patched** |

Patched in `build.gradle` (Direct Upgrade, low breakability):

| Package | From | To |
|---------|------|-----|
| `com.fasterxml.jackson.core:jackson-databind` | 2.21.5 | **2.21.6** |
| `tools.jackson.core:jackson-databind` | 3.1.5 | **3.1.6** |

Snyk IDs addressed: `SNYK-JAVA-COMFASTERXMLJACKSONCORE-19778370` (high), `SNYK-JAVA-TOOLSJACKSONCORE-19778371` (high), plus four medium (CVE-2026-19032, CVE-2026-83557 on both Jackson 2 and 3).

## Snyk Code

Existing design findings, not dependency upgrades:

- Dashboard uses cookie CSRF (CookieCsrfTokenRepository). Ingest POST /api/sessions,/api/events,/api/heartbeat,/api/stop are CSRF-ignored and HMAC-authenticated.
- Format-string in SDK was fixed (msg not in format string).
- Flyway gradle plugin is 13.6.0.

Snyk Secrets is not enabled for the current org.

## Held (not upgraded)

- TypeScript 7
- Spring Boot 4.2.0-M1

## Summary

| Source | Critical | High | Medium | Status |
|--------|----------|------|--------|--------|
| npm audit — frontend | 0 | 0 | 0 | Clean |
| npm audit — sdk | 0 | 0 | 0 | Clean |
| Snyk SCA — frontend/sdk | 0 | 0 | 0 | Clean |
| Snyk SCA — Jackson | 0 | 2→0 | 4→0 | Patched via BOM override |
| Snyk Code | 0 | 1 (CSRF disable, intentional) | 1 | No library change |
