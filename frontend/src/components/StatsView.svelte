<script lang="ts">
  import { t } from '../i18n';
  let { projects = [] }: { projects?: any[] } = $props();
  let projectKey = $state('');
  let loading = $state(false);
  let error = $state('');
  let data = $state<any>(null);

  async function load() {
    loading = true;
    error = '';
    try {
      const q = projectKey ? `?projectKey=${encodeURIComponent(projectKey)}` : '';
      const res = await fetch(`/api/stats/overview${q}`, { credentials: 'same-origin' });
      if (!res.ok) {
        error = $t.statsLoadError;
        data = null;
      } else {
        data = await res.json();
      }
    } catch {
      error = $t.statsLoadError;
      data = null;
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    projectKey;
    load();
  });

  function maxSessions(rows: { sessions: number }[] | undefined): number {
    if (!rows || rows.length === 0) return 1;
    return Math.max(1, ...rows.map(r => r.sessions || 0));
  }
  function barH(v: number, max: number): string {
    return Math.max(2, Math.round((v / max) * 100)) + '%';
  }
  function labelHour(b: string): string {
    const i = b.indexOf('T');
    return i >= 0 ? b.slice(i + 1, i + 6) : b;
  }
  function labelDay(b: string): string {
    return b.length >= 10 ? b.slice(5) : b;
  }
</script>

<div class="view-container">
  <div class="view-header"><div class="vh-left"><h2>{$t.statsTitle}</h2></div></div>
  <div class="view-body">
    <div class="view-main server-config-main">
      <div class="config-card">
        <label class="desc" for="stats-project">{$t.settingsMenuProjects}</label>
        <select id="stats-project" class="cfg-input" bind:value={projectKey}>
          <option value="">{$t.allProjects}</option>
          {#each projects as p (p.apiKey || p.id)}
            <option value={p.apiKey}>{p.name}</option>
          {/each}
        </select>
      </div>
      {#if loading}
        <div class="hint">{$t.loading}</div>
      {:else if error}
        <div class="alert error">{error}</div>
      {:else if data}
        <div class="kpi-row">
          <div class="kpi"><div class="kpi-n">{data.liveActiveUsers}</div><div class="kpi-l">{$t.statsLiveUsers}</div></div>
          <div class="kpi"><div class="kpi-n">{data.liveActiveSessions}</div><div class="kpi-l">{$t.statsLiveSessions}</div></div>
        </div>
        {#each [{title: $t.statsByHour, rows: data.hours, kind: 'h'}, {title: $t.statsByDay, rows: data.days, kind: 'd'}, {title: $t.statsByMonth, rows: data.months, kind: 'm'}] as chart}
          <div class="config-card">
            <h3>{chart.title}</h3>
            {#if !chart.rows || chart.rows.length === 0}
              <div class="hint">{$t.statsEmpty}</div>
            {:else}
              {@const mx = maxSessions(chart.rows)}
              <div class="bars">
                {#each chart.rows as row}
                  <div class="bar-col" title="{row.bucket} · {$t.statsSessions} {row.sessions} · {$t.statsUsers} {row.users}">
                    <div class="bar" style="height: {barH(row.sessions, mx)}"></div>
                    <div class="bar-lab">{chart.kind === 'h' ? labelHour(row.bucket) : chart.kind === 'd' ? labelDay(row.bucket) : row.bucket}</div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>
        {/each}
      {:else}
        <div class="hint">{$t.statsEmpty}</div>
      {/if}
    </div>
  </div>
</div>

<style>
  .server-config-main { max-width: 960px; }
  .config-card {
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 1.1rem 1.25rem;
    margin-bottom: 1rem;
  }
  .config-card h3 { margin: 0 0 0.75rem; font-size: 0.95rem; font-weight: 700; color: var(--text); }
  .desc { display: block; margin: 0 0 0.4rem; font-size: 0.78rem; color: var(--muted); }
  .cfg-input {
    width: 100%; max-width: 360px;
    padding: 0.5rem 0.65rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    font-size: 0.85rem;
    background: var(--bg, #12100e);
    color: var(--text);
  }
  .hint { color: var(--muted); }
  .alert.error {
    background: rgba(212, 106, 92, 0.1);
    border: 1px solid #d46a5c;
    color: #d46a5c;
    padding: 0.5rem 0.75rem;
    border-radius: 6px;
    margin-bottom: 1rem;
  }
  .kpi-row { display: flex; gap: 0.75rem; margin-bottom: 1rem; }
  .kpi {
    flex: 1;
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 0.9rem 1rem;
  }
  .kpi-n { font-size: 1.6rem; font-weight: 700; color: var(--text); font-variant-numeric: tabular-nums; }
  .kpi-l { font-size: 0.72rem; color: var(--muted); margin-top: 0.2rem; }
  .bars {
    display: flex;
    align-items: flex-end;
    gap: 3px;
    height: 120px;
    overflow-x: auto;
  }
  .bar-col {
    flex: 1;
    min-width: 10px;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    align-items: center;
  }
  .bar {
    width: 100%;
    max-width: 18px;
    background: var(--accent, #c45c26);
    border-radius: 2px 2px 0 0;
  }
  .bar-lab {
    font-size: 0.55rem;
    color: var(--muted-2);
    margin-top: 0.25rem;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>