<script lang="ts">
  import { onMount } from 'svelte';
  import { t, locale } from '../i18n';

  let { onClose }: { onClose?: () => void } = $props();

  let loading = $state(false);
  let saving = $state<string | null>(null);
  let error = $state('');
  let success = $state('');
  let purging = $state(false);

  let configs = $state<Record<string, string>>({});

  let retentionDays = $state('30');
  let allowedOrigins = $state('*');
  let projectKey = $state('');

  async function load() {
    loading = true;
    error = '';
    try {
      const res = await fetch('/api/config');
      if (res.ok) {
        const data = await res.json();
        configs = data || {};
        retentionDays = configs['retention-days'] ?? '30';
        allowedOrigins = configs['allowed-capture-origins'] ?? '*';
        projectKey = configs['project-key'] ?? '';
      } else {
        error = 'Failed to load server config';
      }
    } catch {
      error = 'Failed to load server config';
    } finally {
      loading = false;
    }
  }

  async function save(key: string, value: string) {
    saving = key;
    error = '';
    success = '';
    try {
      const res = await fetch(`/api/config/${key}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ value })
      });
      if (res.ok) {
        success = `Saved ${key}`;
        configs[key] = value;
        setTimeout(() => success = '', 1800);
      } else {
        const body = await res.json().catch(() => ({}));
        error = body?.error || `Failed to save ${key}`;
      }
    } catch {
      error = `Failed to save ${key}`;
    } finally {
      saving = null;
    }
  }

  async function purgeRetention() {
    purging = true;
    error = '';
    success = '';
    try {
      const res = await fetch('/api/config/retention/purge', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        const n = data.deleted ?? 0;
        success = $t.serverPurgeResult ? $t.serverPurgeResult(n) : `Purged ${n} sessions`;
        setTimeout(() => success = '', 3000);
      } else {
        const body = await res.json().catch(() => ({}));
        error = body?.error || 'Purge failed';
      }
    } catch {
      error = 'Purge failed';
    } finally {
      purging = false;
    }
  }

  onMount(() => {
    load();
  });
</script>

<div class="view-container">
  <div class="view-header">
    <div class="vh-left">
      <h2>서버 설정 · Server Configuration</h2>
    </div>
  </div>

  <div class="view-body">
    <div class="view-main server-config-main">
      {#if loading}
        <div class="hint">Loading…</div>
      {:else}
        {#if error}
          <div class="alert error">{error}</div>
        {/if}
        {#if success}
          <div class="alert success">{success}</div>
        {/if}

        <div class="config-card">
          <h3>보관 기간 · Retention</h3>
          <p class="desc">Auto-delete sessions older than N days. Set 0 to disable.</p>
          <div class="row">
            <input type="number" bind:value={retentionDays} min="0" step="1" class="cfg-input" />
            <button class="btn btn-primary" disabled={saving==='retention-days'} onclick={() => save('retention-days', String(retentionDays))}>
              {saving === 'retention-days' ? ($locale === 'ko' ? '저장 중…' : 'Saving…') : ($locale === 'ko' ? '저장' : 'Save')}
            </button>
          </div>
          <div class="note">Default 30. Changes take effect on next daily purge (03:30).</div>
          <div class="row" style="margin-top: 0.5rem;">
            <button class="btn btn-primary" disabled={purging} onclick={() => purgeRetention()}>
              {purging ? ($t.serverPurging || 'Purging…') : ($t.serverPurgeNow || 'Manual Purge Now')}
            </button>
          </div>
          <div class="note">{$t.serverPurgeNote || 'Immediately deletes sessions older than current retention days. Use with care.'}</div>
        </div>

        <div class="config-card">
          <h3>캡처 허용 Origin · Allowed Capture Origins</h3>
          <p class="desc">Comma-separated list. No * in production.</p>
          <div class="row">
            <input type="text" bind:value={allowedOrigins} class="cfg-input wide" placeholder="https://app.example.com,https://admin.example.com" />
            <button class="btn btn-primary" disabled={saving==='allowed-capture-origins'} onclick={() => save('allowed-capture-origins', allowedOrigins)}>
              {saving === 'allowed-capture-origins' ? ($locale === 'ko' ? '저장 중…' : 'Saving…') : ($locale === 'ko' ? '저장' : 'Save')}
            </button>
          </div>
          <div class="note">CORS is updated immediately. Restart not required.</div>
        </div>

        <div class="config-card">
          <h3>전역 프로젝트 키 · Global Project Key</h3>
          <p class="desc">Optional fallback when no project is registered.</p>
          <div class="row">
            <input type="text" bind:value={projectKey} class="cfg-input wide" />
            <button class="btn btn-primary" disabled={saving==='project-key'} onclick={() => save('project-key', projectKey)}>
              {saving === 'project-key' ? ($locale === 'ko' ? '저장 중…' : 'Saving…') : ($locale === 'ko' ? '저장' : 'Save')}
            </button>
          </div>
        </div>

        <div class="config-card sensitive">
          <h3>HMAC 비밀키 · HMAC Secret</h3>
          <p class="desc">Used to sign session tokens. <strong>Must be set via environment variable only.</strong></p>
          <div class="note danger">This value is never shown or editable in the UI for security reasons.</div>
          <div class="note">Set <code>LIVESCREENLOG_HMAC_SECRET</code> in your environment / Docker / systemd.</div>
        </div>
      {/if}
    </div>
  </div>
</div>

<style>
  .server-config-main { max-width: 820px; }
  .config-card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 1.1rem 1.25rem;
    margin-bottom: 1rem;
  }
  .config-card.sensitive {
    background: var(--warning-dim);
    border-color: rgba(212, 160, 74, 0.35);
    color: inherit;
  }
  .config-card h3 {
    margin: 0 0 0.35rem;
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--text);
  }
  .desc {
    margin: 0 0 0.6rem;
    font-size: 0.78rem;
    color: var(--muted);
  }
  .row {
    display: flex;
    flex-wrap: wrap;
    gap: 0.6rem;
    align-items: center;
  }
  .cfg-input {
    flex: 1;
    min-width: 0;
    width: 100%;
    max-width: 100%;
    padding: 0.5rem 0.65rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    font-size: 0.85rem;
    background: var(--bg, #12100e);
    color: var(--text);
  }
  .cfg-input.wide {
    min-width: 0;
    width: 100%;
  }
  .note {
    margin-top: 0.5rem;
    font-size: 0.72rem;
    color: var(--muted-2);
  }
  .note.danger {
    color: var(--warning);
    font-weight: 600;
  }
  .alert {
    padding: 0.5rem 0.75rem;
    border-radius: 6px;
    font-size: 0.8rem;
    margin-bottom: 1rem;
  }
  .alert.error {
    background: rgba(212, 106, 92, 0.1);
    border: 1px solid #d46a5c;
    color: #d46a5c;
  }
  .alert.success {
    background: rgba(92, 158, 114, 0.1);
    border: 1px solid #5c9e72;
    color: #5c9e72;
  }
  .hint {
    color: var(--muted);
  }
</style>
