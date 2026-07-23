# Security Policy

## Supported versions

Security fixes are accepted on the default branch of this repository.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security problems.

1. Prefer a private report (GitHub Security Advisory / private contact once the repo is published).
2. Include: affected version or commit, reproduction steps, impact, and any suggested fix.
3. Allow reasonable time for a fix before public disclosure.

## What this project stores

Session replay payloads can include DOM snapshots, user input (unless masked), URLs, and metadata such as `userId`. Treat captured data as **sensitive personal data** under your local privacy laws.

Operators must:

- Set a strong `LIVESCREENLOG_HMAC_SECRET` (32+ random characters) in production.
- Never use default dashboard or DB passwords outside local dev.
- Restrict `LIVESCREENLOG_ALLOWED_CAPTURE_ORIGINS` (no `*` in production).
- Keep read/admin APIs off the public internet unless protected by a reverse proxy, VPN, or equivalent.
- Enable input masking in the browser SDK (`maskAllInputs`, block/ignore classes).
- Configure retention (`LIVESCREENLOG_RETENTION_DAYS`) appropriate for your compliance needs.

## Secrets that must never be committed

| Item | Safe to commit? |
|------|-----------------|
| `.env` with real values | No |
| `FIGMA_API_KEY`, `SNYK_TOKEN`, cloud keys | No |
| Production DB / Redis passwords | No |
| HMAC / project API keys used in prod | No |
| `.env.example` with placeholders only | Yes |

See `docs/OPENSOURCE.md` for the full pre-publish checklist.
