# Security Scan Report

> Snapshot of a one-time local scan. Not a live CI badge. Re-run tools before release.

**Date:** 2026-07-22

## What was scanned

| Target | Tool | Command / scope |
|--------|------|-----------------|
| `frontend/` | npm audit | `npm audit` + `npm audit --json` |
| `sdk/` | npm audit | `npm audit` + `npm audit --json` |
| `frontend/package.json` | Snyk Open Source | `snyk test --file=frontend/package.json --package-manager=npm --severity-threshold=high` |
| `sdk/package.json` | Snyk Open Source | `snyk test --file=sdk/package.json --package-manager=npm --severity-threshold=high` |
| Repository root | Snyk Code | `snyk code test .` |
| MCP packages | npx help | `@playwright/mcp@latest`, `chrome-devtools-mcp@latest` |

Raw outputs were also written under `/tmp/`:

- `/tmp/npm-audit-frontend.json`, `/tmp/npm-audit-frontend.txt`
- `/tmp/npm-audit-sdk.json`, `/tmp/npm-audit-sdk.txt`
- `/tmp/snyk-frontend.txt`, `/tmp/snyk-sdk.txt`, `/tmp/snyk-code.txt`

## npm audit counts

Parsed from `/tmp/npm-audit-frontend.json` and `/tmp/npm-audit-sdk.json` via `jq`.

### Frontend (`frontend/`)

| Severity | Count |
|----------|------:|
| Critical | 0 |
| High | 0 |
| Moderate | 0 |
| Low | 0 |
| Info | 0 |
| **Total** | **0** |

Dependencies (metadata): prod 15, dev 76, optional 33, total 90.

Human summary: `found 0 vulnerabilities`

### SDK (`sdk/`)

| Severity | Count |
|----------|------:|
| Critical | 0 |
| High | 0 |
| Moderate | 0 |
| Low | 0 |
| Info | 0 |
| **Total** | **0** |

Dependencies (metadata): prod 2, dev 67, optional 29, peer 12, total 80.

Human summary: `found 0 vulnerabilities`

## Snyk results

Snyk did **not** return vulnerability counts. Scans failed before producing a report.

| Scan | Result | Notes |
|------|--------|--------|
| Open Source — frontend | Failed | `SNYK-0003` — Client request cannot be processed (HTTP 400 Bad Request) |
| Open Source — sdk | Failed | `SNYK-0003` — Client request cannot be processed (HTTP 400 Bad Request) |
| Code — repository | Failed | `SNYK-0005` — Authentication error (HTTP 401 Unauthorized). Message: use `snyk auth` |

### Snyk auth required?

**Yes.**

- `snyk code test` explicitly requires authentication (`SNYK-0005` / 401).
- Open Source tests returned `SNYK-0003` (400) rather than a clean vuln report; after authenticating with `snyk auth`, re-run all three Snyk commands. Auth may also be needed or helpful for Open Source depending on account/token state.

Snyk severity counts: **unavailable** (scans did not complete).

## Top issues

### npm audit

No vulnerabilities reported for frontend or SDK. No package names or fix versions to list.

### Snyk

No issue inventory available (auth / client errors).

### MCP package verification

Both packages resolved and printed help successfully:

- `@playwright/mcp@latest` — help/usage OK
- `chrome-devtools-mcp@latest` — help/usage OK

No install/runtime failures observed for `--help` checks. (npx also emitted a deprecation notice for transitive `boolean@3.2.0` while pulling Snyk; not an app dependency finding from npm audit.)

## Recommended next actions

1. **Authenticate Snyk and re-scan**
   - Run `snyk auth`
   - Re-run:
     - `npx -y snyk@latest test --file=frontend/package.json --package-manager=npm --severity-threshold=high`
     - `npx -y snyk@latest test --file=sdk/package.json --package-manager=npm --severity-threshold=high`
     - `npx -y snyk@latest code test .`
   - If `SNYK-0003` persists after auth, run with debug and doctor as suggested: `snyk <command> -d 2>&1 | snyk doctor --stdin`

2. **Keep npm audit clean**
   - Continue running `npm audit` in CI for `frontend/` and `sdk/`
   - Prefer `npm audit fix` (non-breaking) when future findings appear; review breaking upgrades before `npm audit fix --force`

3. **Optional hardening**
   - Pin/lockfile reviews on dependency upgrades
   - Add periodic Snyk (or equivalent SCA + SAST) to CI once auth/org token is available
   - Monitor MCP tooling separately from app runtime deps (`@playwright/mcp`, `chrome-devtools-mcp` are dev/tooling, not production app bundles unless explicitly shipped)

4. **No immediate dependency upgrades required** from this npm audit pass (0 findings).

## Summary

| Source | Critical | High | Medium/Moderate | Status |
|--------|----------|------|-----------------|--------|
| npm audit — frontend | 0 | 0 | 0 | Clean |
| npm audit — sdk | 0 | 0 | 0 | Clean |
| Snyk Open Source | — | — | — | Failed (SNYK-0003) |
| Snyk Code | — | — | — | Failed — **auth required** (SNYK-0005) |
