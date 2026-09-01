<script lang="ts">
  import { t } from '../i18n';
  let { onClose }: { onClose?: () => void } = $props();
  let currentPassword = $state('');
  let newPassword = $state('');
  let confirmPassword = $state('');
  let saving = $state(false);
  let error = $state('');
  let success = $state('');

  async function submit(e: Event) {
    e.preventDefault();
    error = '';
    success = '';
    if (newPassword.length < 8) {
      error = $t.accountTooShort;
      return;
    }
    if (newPassword !== confirmPassword) {
      error = $t.accountMismatch;
      return;
    }
    saving = true;
    try {
      const res = await fetch('/api/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ currentPassword, newPassword })
      });
      if (res.status === 401) {
        window.location.href = '/login';
        return;
      }
      const body = await res.json().catch(() => ({}));
      if (res.ok) {
        success = $t.accountSuccess;
        currentPassword = '';
        newPassword = '';
        confirmPassword = '';
      } else {
        error = body.error || $t.accountTooShort;
      }
    } catch {
      error = $t.accountTooShort;
    } finally {
      saving = false;
    }
  }
</script>

<div class="view-shell">
  <div class="view-main server-config-main">
    {#if error}<div class="alert error">{error}</div>{/if}
    {#if success}<div class="alert success">{success}</div>{/if}
    <div class="config-card">
      <h3>{$t.settingsMenuAccount}</h3>
      <p class="desc">{$t.settingsAccountHint}</p>
      <form onsubmit={submit}>
        <label class="cfg-label">{$t.accountCurrentPassword}</label>
        <input class="cfg-input wide" type="password" bind:value={currentPassword} autocomplete="current-password" />
        <label class="cfg-label">{$t.accountNewPassword}</label>
        <input class="cfg-input wide" type="password" bind:value={newPassword} autocomplete="new-password" />
        <label class="cfg-label">{$t.accountConfirmPassword}</label>
        <input class="cfg-input wide" type="password" bind:value={confirmPassword} autocomplete="new-password" />
        <div class="row" style="margin-top: 0.75rem;">
          <button class="btn btn-primary" type="submit" disabled={saving}>
            {saving ? $t.accountSaving : $t.accountSave}
          </button>
        </div>
      </form>
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
  .config-card h3 { margin: 0 0 0.35rem; }
  .desc { color: var(--muted); font-size: 0.85rem; margin: 0 0 1rem; }
  .cfg-label { display: block; font-size: 0.78rem; font-weight: 600; color: var(--muted); margin: 0.6rem 0 0.25rem; }
  .cfg-input {
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--text);
    padding: 0.55rem 0.75rem;
    border-radius: 4px;
    width: 100%;
  }
  .cfg-input.wide { max-width: 420px; }
  .row { display: flex; gap: 0.5rem; }
  .btn { padding: 0.55rem 1rem; border: none; border-radius: 4px; font-weight: 700; cursor: pointer; }
  .btn-primary { background: #c45c26; color: white; }
  .btn-primary:disabled { opacity: 0.6; }
  .alert { padding: 0.5rem 0.75rem; border-radius: 4px; margin-bottom: 0.75rem; font-size: 0.85rem; }
  .alert.error { background: rgba(212,106,92,0.1); border: 1px solid #d46a5c; color: #d46a5c; }
  .alert.success { background: rgba(92,158,114,0.1); border: 1px solid #5c9e72; color: #5c9e72; }
</style>