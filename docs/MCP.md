# MCP setup (Figma + Playwright + Chrome DevTools + Snyk)

This project enables four local MCP servers in `kilo.json`:

| Server | Package | Purpose |
|--------|---------|---------|
| `figma` | `figma-developer-mcp` (Framelink) | Design source — read Figma frames/design context for UI implementation |
| `playwright` | `@playwright/mcp@latest` | Browser UI automation, screenshots, visual verification |
| `chrome-devtools` | `chrome-devtools-mcp@latest` | Live DOM/CSS inspection, performance / Lighthouse-related tooling |
| `snyk` | `snyk@latest mcp` | Code and dependency vulnerability scanning |

Permissions default to ask for MCP tools:

- `figma_*`
- `playwright_*`
- `chrome-devtools_*` / `chrome_devtools_*`
- `snyk_*`

## Figma (`FIGMA_API_KEY`) — design source

1. Create a Personal Access Token: **Figma → Settings → Security → Personal access tokens**
2. Scopes: **File content (read)**, **Dev resources (read)**
3. Export before starting Kilo (preferred — no secrets in git):

```bash
export FIGMA_API_KEY=figd_your_token_here
```

Or add `FIGMA_API_KEY=...` to a local `.env` if your shell/Kilo launch loads it. Do **not** commit real keys.

The figma MCP entry omits `environment` so the parent process env (including `FIGMA_API_KEY`) is inherited. An empty `FIGMA_API_KEY: ""` in config would override and break auth.

Restart Kilo after setting the key. Confirm with `/mcps`.

### Example prompts

- Implement this Figma frame: `https://www.figma.com/design/...`
- Extract spacing, colors, and typography from this Figma node and match the existing design system.
- Pull component specs from this Figma file and list missing tokens vs our CSS variables.

## Playwright — UI automation / screenshots

Uses `@playwright/mcp` for headless/headed browser control, navigation, clicks, and screenshots. Useful for visual regression checks and verifying UI after implementation.

No API key required. First run may download browser binaries via Playwright.

### Example prompts

- Open `http://localhost:3000`, take a full-page screenshot, and compare layout to the Figma frame.
- Click through the session replay timeline and capture screenshots of each major state.
- Verify the filter drawer opens, applies filters, and the table updates without console errors.
- Run a quick smoke: load dashboard → open a session → play/pause → confirm controls respond.

## Chrome DevTools — DOM/CSS/performance inspection

Uses `chrome-devtools-mcp` for live page inspection (DOM, computed styles, network, performance). Complements Playwright when you need DevTools-level detail rather than pure automation.

No API key required. Requires a Chrome/Chromium-compatible browser available on the machine.

### Example prompts

- Inspect the session player container: computed layout, overflow, and z-index stacking.
- Check network waterfalls for the replay API and flag slow or failed requests.
- Run a performance pass on the dashboard list and summarize main-thread long tasks.
- Dump accessibility tree issues for the filter form and suggest fixes.

## Snyk — security

Usually authenticate once via MCP (no token in config):

1. Ask the agent to run `snyk_auth`
2. Ask the agent to run `snyk_trust` on the project path
3. Then scan

Optional: set `SNYK_TOKEN` in the environment if you prefer token auth (see `.env.example`).

CLI scans without auth will fail with an auth error; see `docs/SECURITY_SCAN.md` for npm audit results that do not require Snyk.

### Example prompts

- Scan this project for code and dependency vulnerabilities
- Run a Snyk open-source dependency scan and summarize fixable issues
- Check for high/critical issues only and propose upgrades

## Verify

```bash
# Packages resolve
npx -y figma-developer-mcp --help
npx -y @playwright/mcp@latest --help
npx -y chrome-devtools-mcp@latest --help
npx -y snyk@latest --version

# In Kilo
/mcps
```

## Global config

The same `mcp` block (and matching permission globs) is also in `~/.config/kilo/kilo.json` so these servers are available across projects. Project `./kilo.json` overrides on merge.
