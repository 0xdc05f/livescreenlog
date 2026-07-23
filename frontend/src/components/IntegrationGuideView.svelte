<script lang="ts">
  import { t } from '../i18n';

  let { projects = [], onClose = undefined } = $props();

  let copied = $state<string | null>(null);
  let selectedProjectId = $state<number | string>('');

  let selectedProject = $derived(
    projects.find((p: any) => String(p.id) === String(selectedProjectId)) ?? null
  );
  let apiKey = $derived(selectedProject?.apiKey || 'YOUR_API_KEY');

  async function copy(text: string, key: string) {
    try {
      await navigator.clipboard.writeText(text);
      copied = key;
      setTimeout(() => copied = null, 2000);
    } catch {}
  }

  function highlight(code: string): string {
    const lines = code.split('\n');
    const highlightedLines = lines.map(line => {
      const trimmed = line.trim();
      if (trimmed.startsWith('<!--') || trimmed.startsWith('&lt;!--')) {
        return `<span class="hl-cmt">${escapeHtml(line)}</span>`;
      }
      if (trimmed.startsWith('//') || trimmed.startsWith('#')) {
        return `<span class="hl-cmt">${escapeHtml(line)}</span>`;
      }
      let escaped = escapeHtml(line);
      const strings: string[] = [];
      escaped = escaped.replace(/(['"`])(.*?)\1/g, (match) => {
        strings.push(match);
        return `__STR_PLACEHOLDER_${strings.length - 1}__`;
      });
      escaped = escaped.replace(/(&lt;\/?script.*?&gt;)/g, '<span class="hl-tag">$1</span>');
      const keywords = /\b(const|let|var|function|return|new|await|async|import|export|from|typeof|void|true|false|null|undefined)\b/g;
      escaped = escaped.replace(keywords, '<span class="hl-kw">$1</span>');
      const builtins = /\b(fetch|window|document|navigator|JSON|LiveScreenLog)\b/g;
      escaped = escaped.replace(builtins, '<span class="hl-builtin">$1</span>');
      const numbers = /\b(\d+)\b/g;
      escaped = escaped.replace(numbers, '<span class="hl-num">$1</span>');
      for (let i = 0; i < strings.length; i++) {
        escaped = escaped.replace(`__STR_PLACEHOLDER_${i}__`, `<span class="hl-str">${strings[i]}</span>`);
      }
      return escaped;
    });
    return highlightedLines.join('\n');
  }

  function escapeHtml(str: string): string {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  let host = $derived(typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');

  let browserSnippet = $derived(`<script src="${host}/livescreenlog.js"><\/script>
<script>
  LiveScreenLog.init({
    endpoint: '${host}',
    apiKey: '${apiKey}',
    id: '사번',
  });
  LiveScreenLog.setTags({ dept: '영업팀' });
<\/script>`);

  let npmSnippet = $derived(`// npm i @livescreenlog/browser
// or: import from '${host}/livescreenlog.js'

import { LiveScreenLog } from '@livescreenlog/browser';

LiveScreenLog.init({
  endpoint: '${host}',
  apiKey: '${apiKey}',
  id: 'user-001',
});
LiveScreenLog.setTags({ role: 'admin', app: 'web' });`);

  let vueSnippet = $derived(`import { LiveScreenLog } from '@livescreenlog/browser';
// or window.LiveScreenLog after <script src="${host}/livescreenlog.js">

// e.g. after login
LiveScreenLog.init({
  endpoint: '${host}',
  apiKey: '${apiKey}',
  id: user.empNo,
  integration: 'vue',
});
LiveScreenLog.setTags({ dept: user.dept, app: 'vue-admin' });`);

  let apiSessionInit = $derived(`POST /api/sessions
Content-Type: application/json

{
  "apiKey": "${apiKey}",
  "userId": "user001",
  "tags": { "dept": "영업팀" },
  "sdkName": "livescreenlog-browser",
  "sdkVersion": "1.1.0",
  "sdkIntegration": "browser"
}

// → { sessionId, token, enabled, recordingMode }`);

  let apiEvents = `POST /api/events
Content-Type: application/json
x-livescreenlog-session-token: <token>

[ { "type": 3, "data": {}, "timestamp": 0 } ]`;

  let apiHeartbeat = `POST /api/heartbeat
x-livescreenlog-session-token: <token>`;

  let apiStop = `POST /api/stop
x-livescreenlog-session-token: <token>`;

  let apiSse = $derived(`GET /api/push/connect?projectKey=${apiKey}&userId=user001
Accept: text/event-stream`);
</script>

<div class="view-container">
  <div class="view-header">
    <div class="vh-left">
      <h2>{$t.guideTitle}</h2>
    </div>
    {#if onClose}
      <button class="btn btn-ghost" onclick={onClose}>← {$t.settingsMenuReturn}</button>
    {/if}
  </div>

  <div class="view-body">
    <div class="view-main">

      <div class="step-card">
        <div class="step-header">
          <span class="step-num">0</span>
          <div>
            <h3>{$t.guideStep0}</h3>
            <p class="step-desc">{$t.guideStep0Desc}</p>
          </div>
        </div>
        {#if !projects?.length}
          <div class="empty-hint">{$t.guideNoProjects}</div>
        {:else}
          <select class="project-select" bind:value={selectedProjectId}>
            <option value="">{$t.guideSelectProject}</option>
            {#each projects as p}
              <option value={p.id}>{p.name}</option>
            {/each}
          </select>
          {#if selectedProject}
            <div class="key-preview">
              <span class="key-label">API Key</span>
              <code class="key-value">{selectedProject.apiKey}</code>
            </div>
          {/if}
        {/if}
      </div>

      <div class="step-card">
        <div class="step-header">
          <span class="step-num">1</span>
          <div class="step-title-row">
            <div>
              <h3>{$t.guideStep1}</h3>
              <p class="step-desc">{$t.guideStep1Desc}</p>
            </div>
            <button class="btn btn-ghost copy-btn" onclick={() => copy(browserSnippet, 'browser')}>
              {copied === 'browser' ? $t.guideCopied : $t.guideCopy}
            </button>
          </div>
        </div>
        <div class="editor-window">
          <div class="editor-header">
            <div class="window-dots">
              <span class="dot red"></span>
              <span class="dot yellow"></span>
              <span class="dot green"></span>
            </div>
            <span class="editor-tab-name">index.html</span>
          </div>
          <pre class="code-block"><code>{@html highlight(browserSnippet)}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="step-header">
          <span class="step-num">2</span>
          <div class="step-title-row">
            <div>
              <h3>{$t.guideStep1b}</h3>
              <p class="step-desc">{$t.guideStep1bDesc}</p>
            </div>
            <button class="btn btn-ghost copy-btn" onclick={() => copy(npmSnippet, 'npm')}>
              {copied === 'npm' ? $t.guideCopied : $t.guideCopy}
            </button>
          </div>
        </div>
        <div class="editor-window">
          <div class="editor-header">
            <div class="window-dots">
              <span class="dot red"></span>
              <span class="dot yellow"></span>
              <span class="dot green"></span>
            </div>
            <span class="editor-tab-name">app.js</span>
          </div>
          <pre class="code-block"><code>{@html highlight(npmSnippet)}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="step-header">
          <span class="step-num">3</span>
          <div class="step-title-row">
            <div>
              <h3>{$t.guideStep1c}</h3>
              <p class="step-desc">{$t.guideStep1cDesc}</p>
            </div>
            <button class="btn btn-ghost copy-btn" onclick={() => copy(vueSnippet, 'vue')}>
              {copied === 'vue' ? $t.guideCopied : $t.guideCopy}
            </button>
          </div>
        </div>
        <div class="editor-window">
          <div class="editor-header">
            <div class="window-dots">
              <span class="dot red"></span>
              <span class="dot yellow"></span>
              <span class="dot green"></span>
            </div>
            <span class="editor-tab-name">main.ts / App.vue</span>
          </div>
          <pre class="code-block"><code>{@html highlight(vueSnippet)}</code></pre>
        </div>
      </div>

      <div class="section-intro">
        <h3>{$t.guideStep2}</h3>
        <p>{$t.guideStep2Desc}</p>
      </div>

      <div class="step-card">
        <div class="cc-header">
          <div class="api-endpoint-title">
            <span class="method post">POST</span>
            <h4>{$t.guideApiSession}</h4>
          </div>
          <button class="btn btn-ghost copy-btn" onclick={() => copy(apiSessionInit, 'api-sess')}>
            {copied === 'api-sess' ? $t.guideCopied : $t.guideCopy}
          </button>
        </div>
        <p class="subtitle">{$t.guideApiSessionDesc}</p>
        <div class="editor-window">
          <pre class="code-block text-block"><code>{apiSessionInit}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="cc-header">
          <div class="api-endpoint-title">
            <span class="method post">POST</span>
            <h4>{$t.guideApiEvents}</h4>
          </div>
          <button class="btn btn-ghost copy-btn" onclick={() => copy(apiEvents, 'api-ev')}>
            {copied === 'api-ev' ? $t.guideCopied : $t.guideCopy}
          </button>
        </div>
        <p class="subtitle">{$t.guideApiEventsDesc}</p>
        <div class="editor-window">
          <pre class="code-block text-block"><code>{apiEvents}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="cc-header">
          <div class="api-endpoint-title">
            <span class="method post">POST</span>
            <h4>{$t.guideApiHeartbeat}</h4>
          </div>
          <button class="btn btn-ghost copy-btn" onclick={() => copy(apiHeartbeat, 'api-hb')}>
            {copied === 'api-hb' ? $t.guideCopied : $t.guideCopy}
          </button>
        </div>
        <p class="subtitle">{$t.guideApiHeartbeatDesc}</p>
        <div class="editor-window">
          <pre class="code-block text-block"><code>{apiHeartbeat}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="cc-header">
          <div class="api-endpoint-title">
            <span class="method post">POST</span>
            <h4>{$t.guideApiStop}</h4>
          </div>
          <button class="btn btn-ghost copy-btn" onclick={() => copy(apiStop, 'api-stop')}>
            {copied === 'api-stop' ? $t.guideCopied : $t.guideCopy}
          </button>
        </div>
        <p class="subtitle">{$t.guideApiStopDesc}</p>
        <div class="editor-window">
          <pre class="code-block text-block"><code>{apiStop}</code></pre>
        </div>
      </div>

      <div class="step-card">
        <div class="cc-header">
          <div class="api-endpoint-title">
            <span class="method get">GET</span>
            <h4>{$t.guideApiSse}</h4>
          </div>
          <button class="btn btn-ghost copy-btn" onclick={() => copy(apiSse, 'api-sse')}>
            {copied === 'api-sse' ? $t.guideCopied : $t.guideCopy}
          </button>
        </div>
        <p class="subtitle">{$t.guideApiSseDesc}</p>
        <div class="editor-window">
          <pre class="code-block text-block"><code>{apiSse}</code></pre>
        </div>
      </div>

      <div class="step-card confirm-card">
        <div class="step-header">
          <span class="step-num check">✓</span>
          <div>
            <h3>{$t.guideConfirmTitle}</h3>
            <p class="step-desc">{$t.guideConfirmDesc}</p>
          </div>
        </div>
      </div>

    </div>
  </div>
</div>

<style>
  .view-container {
    display: flex;
    flex-direction: column;
    flex: 1;
    height: 100%;
    background: var(--bg);
    overflow: hidden;
  }

  .view-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1.1rem 1.75rem;
    border-bottom: 1px solid var(--border);
    background: var(--panel);
  }

  .vh-left h2 {
    font-size: 1.05rem;
    font-weight: 700;
    margin: 0;
    color: var(--text);
    letter-spacing: -0.01em;
  }

  .view-body {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  .view-main {
    flex: 1;
    padding: 1.75rem;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 1.15rem;
    background: var(--bg);
    max-width: 920px;
  }

  .step-card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 1.15rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .confirm-card {
    background: rgba(99, 102, 241, 0.06);
    border-color: rgba(99, 102, 241, 0.22);
  }

  .step-header {
    display: flex;
    align-items: flex-start;
    gap: 0.85rem;
  }

  .step-title-row {
    flex: 1;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 0.75rem;
  }

  .step-num {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: 8px;
    background: rgba(99, 102, 241, 0.15);
    color: var(--accent);
    font-size: 0.75rem;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .step-num.check {
    background: rgba(16, 185, 129, 0.15);
    color: #10b981;
  }

  .step-header h3,
  .section-intro h3 {
    margin: 0 0 0.25rem;
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--text);
  }

  .step-desc,
  .section-intro p,
  .subtitle {
    margin: 0;
    font-size: 0.75rem;
    color: var(--muted);
    line-height: 1.45;
  }

  .section-intro {
    margin-top: 0.5rem;
    padding-top: 0.5rem;
  }

  .project-select {
    width: 100%;
    max-width: 360px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 7px;
    color: var(--text);
    font-size: 0.82rem;
    padding: 0.55rem 0.75rem;
    outline: none;
    font-family: inherit;
  }

  .project-select:focus {
    border-color: var(--accent);
  }

  .empty-hint {
    font-size: 0.78rem;
    color: var(--muted);
    padding: 0.65rem 0.8rem;
    background: var(--surface);
    border: 1px dashed var(--border);
    border-radius: 7px;
  }

  .key-preview {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 7px;
    padding: 0.5rem 0.75rem;
    max-width: 100%;
  }

  .key-label {
    font-size: 0.68rem;
    font-weight: 700;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
    flex-shrink: 0;
  }

  .key-value {
    font-family: monospace;
    font-size: 0.75rem;
    color: var(--text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .cc-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 0.75rem;
  }

  .api-endpoint-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .api-endpoint-title h4 {
    margin: 0;
    font-size: 0.88rem;
    font-weight: 700;
    color: var(--text);
  }

  .method {
    font-size: 0.65rem;
    font-weight: 800;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    color: white;
    font-family: monospace;
  }

  .method.post { background: #10b981; }
  .method.get { background: #3b82f6; }

  .copy-btn {
    flex-shrink: 0;
    font-size: 0.72rem !important;
  }

  .editor-window {
    display: flex;
    flex-direction: column;
    background: #050507;
    border: 1px solid var(--border);
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 4px 20px rgba(0,0,0,0.3);
  }

  .editor-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #111216;
    padding: 0.5rem 1rem;
    border-bottom: 1px solid var(--border);
  }

  .window-dots {
    display: flex;
    gap: 6px;
  }

  .window-dots .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }

  .window-dots .dot.red { background: #ff5f56; }
  .window-dots .dot.yellow { background: #ffbd2e; }
  .window-dots .dot.green { background: #27c93f; }

  .editor-tab-name {
    font-family: 'SFMono-Regular', Consolas, monospace;
    font-size: 0.7rem;
    color: var(--muted);
    font-weight: 600;
  }

  .code-block {
    background: transparent;
    padding: 1.25rem;
    font-family: 'SFMono-Regular', Consolas, 'Fira Code', monospace;
    font-size: 0.74rem;
    color: var(--text);
    overflow-x: auto;
    white-space: pre;
    max-height: 320px;
    margin: 0;
    line-height: 1.5;
  }

  .text-block {
    color: #94a3b8;
  }

  :global(.hl-str) { color: #4ade80; }
  :global(.hl-cmt) { color: #64748b; font-style: italic; }
  :global(.hl-tag) { color: #f43f5e; }
  :global(.hl-num) { color: #fb923c; }
  :global(.hl-kw) { color: #c084fc; font-weight: 600; }
  :global(.hl-builtin) { color: #60a5fa; }
</style>
