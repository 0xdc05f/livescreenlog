<script lang="ts">
  import { onMount } from 'svelte';
  import { t } from '../i18n';
  import { buildSessionTags, formatSdkBadge, parseUserAgent } from '../lib/uaParse';
  import { formatCompact, formatRange } from '../lib/dateFormat';
  import DeviceIcons from '../components/DeviceIcons.svelte';

  let { onSelect, selectedSessionId, projects = [] } = $props();

  let sessions = $state<any[]>([]);
  let loading = $state(true);
  let listError = $state('');
  let queryStr = $state('');
  let source = $state('');
  let selectedProjectKey = $state('');
  let selectedStatus = $state('');
  let page = $state(0);
  let totalPages = $state(0);
  let totalElements = $state(0);

  let startDate = $state('');
  let endDate = $state('');
  let activePreset = $state('all');

  let advancedActive = $derived(
    !!(selectedProjectKey || source || (activePreset !== 'all'))
  );
  let advancedCount = $derived(
    (selectedProjectKey ? 1 : 0) +
    (source ? 1 : 0) +
    (activePreset !== 'all' ? 1 : 0)
  );
  let filtersOpen = $state(false);

  const statusOptions = [
    { key: '', label: () => $t.filterAll, dot: '' },
    { key: 'ACTIVE', label: () => $t.filterLive, dot: 'green' },
    { key: 'STOPPED', label: () => $t.filterEnded, dot: 'gray' },
  ];

  let debounceTimeout: any;
  function handleInputDebounce() {
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(() => {
      fetchSessions(0);
    }, 300);
  }

  function applyPreset(key: string) {
    activePreset = key;
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    switch (key) {
      case 'all': startDate = ''; endDate = ''; break;
      case 'today': startDate = toDateStr(today); endDate = toDateStr(today); break;
      case 'yesterday': {
        const y = new Date(today); y.setDate(y.getDate() - 1);
        startDate = toDateStr(y); endDate = toDateStr(y); break;
      }
      case '7d': {
        const s = new Date(today); s.setDate(s.getDate() - 6);
        startDate = toDateStr(s); endDate = toDateStr(today); break;
      }
      case '30d': {
        const s = new Date(today); s.setDate(s.getDate() - 29);
        startDate = toDateStr(s); endDate = toDateStr(today); break;
      }
    }
    if (key !== 'custom') fetchSessions(0);
  }

  function toDateStr(d: Date) { return d.toISOString().split('T')[0]; }
  function toIsoStart(s: string) { return s ? new Date(s + 'T00:00:00').toISOString() : ''; }
  function toIsoEnd(s: string)   { return s ? new Date(s + 'T23:59:59').toISOString() : ''; }

  async function fetchSessions(pageToFetch = 0) {
    loading = true;
    listError = '';
    try {
      const q = new URLSearchParams();
      if (queryStr) q.append('query', queryStr);
      if (source) q.append('source', source);
      if (startDate) q.append('startDate', toIsoStart(startDate));
      if (endDate) q.append('endDate', toIsoEnd(endDate));
      if (selectedProjectKey) q.append('projectKey', selectedProjectKey);
      if (selectedStatus) q.append('status', selectedStatus);
      q.append('page', pageToFetch.toString());
      q.append('size', '20');
      q.append('sort', 'createdAt,desc');
      const res = await fetch(`/api/sessions?${q.toString()}`);
      if (res.ok) {
        const data = await res.json();
        sessions = data.content || [];
        page = data.pageable?.pageNumber ?? 0;
        totalPages = data.totalPages ?? 0;
        totalElements = data.totalElements ?? 0;
      } else {
        listError = $t.listError;
        sessions = [];
      }
    } catch (e) {
      console.error(e);
      listError = $t.listError;
      sessions = [];
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    if (advancedActive) filtersOpen = true;
    fetchSessions();
  });

  function getSessionStatus(session: any): { label: string; cls: string } {
    if (session.status === 'STOPPED') return { label: $t.statusEnded, cls: 'status-ended' };
    const diffMin = (Date.now() - new Date(session.updatedAt).getTime()) / 60000;
    if (diffMin < 2)  return { label: $t.statusLive, cls: 'status-live' };
    if (diffMin < 30) return { label: $t.statusIdle, cls: 'status-idle' };
    return { label: $t.statusEnded, cls: 'status-ended' };
  }

  function relativeDate(iso: string): string {
    const diffMin = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (diffMin < 1)  return $t.justNow;
    if (diffMin < 60) return $t.minutesAgo(diffMin);
    const h = Math.floor(diffMin / 60);
    if (h < 24) return $t.hoursAgo(h);
    return new Date(iso).toLocaleDateString();
  }

  function getProjectName(projectKey: string): string | null {
    if (!projectKey) return null;
    const p = projects.find((p: any) => p.apiKey === projectKey);
    return p?.name ?? null;
  }

  function cardTitle(session: any): string {
    return session.projectName
      ?? getProjectName(session.projectKey)
      ?? $t.noSystem;
  }

  function sessionTags(session: any) {
    return buildSessionTags(session, {
      user: $t.tagUser,
      dept: $t.tagDept,
    });
  }

  function tagIconFor(session: any, tag: { kind: string }): string {
    const info = parseUserAgent(session.distinctId);
    if (tag.kind === 'browser') return info?.browserKey ?? 'chrome';
    if (tag.kind === 'os') return info?.osKey ?? 'windows';
    if (tag.kind === 'device') return info?.deviceKey ?? 'desktop';
    if (tag.kind === 'user') return 'user';
    if (tag.kind === 'dept') return 'dept';
    if (tag.kind === 'custom') return 'other';
    return 'other';
  }

  function sdkBadge(session: any): string | null {
    return formatSdkBadge(session);
  }

  function isEnded(session: any): boolean {
    if (session.status === 'STOPPED') return true;
    const diffMin = (Date.now() - new Date(session.updatedAt).getTime()) / 60000;
    return diffMin >= 30;
  }

  function sessionTimeLabel(session: any): string {
    if (isEnded(session)) {
      return formatRange(session.createdAt, session.endAt || session.updatedAt);
    }
    // Live/idle: relative + compact start
    const rel = relativeDate(session.createdAt);
    const abs = formatCompact(session.createdAt);
    return `${rel} · ${abs}`;
  }

  function anyFilterActive(): boolean {
    return !!(queryStr || selectedStatus || selectedProjectKey || source || activePreset !== 'all');
  }

  function clearFilters() {
    queryStr = '';
    source = '';
    selectedProjectKey = '';
    selectedStatus = '';
    activePreset = 'all';
    startDate = '';
    endDate = '';
    filtersOpen = false;
    fetchSessions(0);
  }
</script>

<div class="session-list-root">
  <div class="panel-head">
    <span class="panel-title">{$t.panelSessions}</span>
    <span class="count-badge">{totalElements}</span>
  </div>

  <div class="search-wrap">
    <div class="search-input-wrap-clean">
      <input
        class="search-input"
        placeholder={$t.searchPlaceholder}
        bind:value={queryStr}
        oninput={handleInputDebounce}
      />
    </div>
  </div>

  <div class="filter-area">
    <div class="status-chips">
      {#each statusOptions as opt}
        <button
          type="button"
          class="filter-chip"
          class:active={selectedStatus === opt.key}
          onclick={() => { selectedStatus = opt.key; fetchSessions(0); }}
        >
          {#if opt.dot === 'green'}
            <span class="dot-live live-pulse"></span>
          {:else if opt.dot === 'gray'}
            <span class="dot-ended"></span>
          {/if}
          {opt.label()}
        </button>
      {/each}
    </div>

    <div class="filters-toggle-row">
      <button
        type="button"
        class="filters-toggle"
        class:open={filtersOpen}
        onclick={() => filtersOpen = !filtersOpen}
      >
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="filters-toggle-icon">
          <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon>
        </svg>
        {$t.filtersToggle}
        {#if advancedCount > 0}
          <span class="filters-badge">{advancedCount}</span>
        {/if}
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="filters-chevron" class:open={filtersOpen}>
          <polyline points="6 9 12 15 18 9"></polyline>
        </svg>
      </button>
      {#if advancedActive}
        <button type="button" class="filters-clear" onclick={clearFilters}>
          {$t.filtersClear}
        </button>
      {/if}
    </div>

    {#if filtersOpen}
      <div class="advanced-filters">
        {#if projects.length > 0}
          <div class="filter-row">
            <select class="filter-input" bind:value={selectedProjectKey}
              onchange={() => fetchSessions(0)}>
              <option value="">{$t.allProjects}</option>
              {#each projects as p}
                <option value={p.apiKey}>{p.name}</option>
              {/each}
            </select>
          </div>
        {/if}

        <div class="filter-row">
          <input
            class="filter-input"
            placeholder={$t.filterSource}
            bind:value={source}
            oninput={handleInputDebounce}
          />
        </div>

        <div class="date-chips">
          {#each [
            { key: 'all', label: () => $t.dateAll },
            { key: 'today', label: () => $t.dateToday },
            { key: 'yesterday', label: () => $t.dateYesterday },
            { key: '7d', label: () => $t.date7d },
            { key: '30d', label: () => $t.date30d },
            { key: 'custom', label: () => $t.dateCustom },
          ] as opt}
            <button
              type="button"
              class="filter-chip"
              class:active={activePreset === opt.key}
              onclick={() => applyPreset(opt.key)}
            >{opt.label()}</button>
          {/each}
        </div>

        {#if activePreset === 'custom'}
          <div class="custom-date-box">
            <div class="custom-date-title">{$t.dateCustomTitle}</div>
            <div class="custom-date-row">
              <span class="custom-date-label">{$t.dateFrom}</span>
              <input
                type="date"
                class="filter-input"
                bind:value={startDate}
                onchange={() => fetchSessions(0)}
              />
            </div>
            <div class="custom-date-row">
              <span class="custom-date-label">{$t.dateTo}</span>
              <input
                type="date"
                class="filter-input"
                bind:value={endDate}
                onchange={() => fetchSessions(0)}
              />
            </div>
          </div>
        {/if}
      </div>
    {/if}
  </div>

  <div class="sessions-list">
    {#if loading}
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="loading-clean">{$t.loadingSessions}</div>
    {:else if listError}
      <div class="empty-state-clean error-state">
        <p>{listError}</p>
        <button type="button" class="btn-secondary" onclick={() => fetchSessions(page)}>
          {$t.btnSearch}
        </button>
      </div>
    {:else if sessions.length === 0}
      <div class="empty-state-clean">
        <p>{$t.noSessions}</p>
        {#if anyFilterActive()}
          <button type="button" class="btn-secondary" onclick={clearFilters}>
            {$t.filtersClear}
          </button>
        {/if}
      </div>
    {:else}
      {#each sessions as session}
        {@const st = getSessionStatus(session)}
        <button
          type="button"
          class="session-card"
          class:active={selectedSessionId === session.sessionId}
          onclick={() => onSelect(session)}
        >
          <div class="sc-top">
            <span class="sc-system">{cardTitle(session)}</span>
            <span class="sc-status-badge {st.cls}">
              {#if st.cls === 'status-live'}
                <span class="dot-live live-pulse"></span>
              {/if}
              {st.label}
            </span>
          </div>

          <div class="sc-tags">
            {#each sessionTags(session) as tag}
              <span class="meta-tag kind-{tag.kind}" title="{tag.label}: {tag.value}">
                <DeviceIcons kind={tagIconFor(session, tag)} size={11} />
                {#if tag.kind === 'user' || tag.kind === 'dept' || tag.kind === 'custom'}
                  <span class="meta-tag-label">{tag.label}</span>
                {/if}
                <span class="meta-tag-value">{tag.value}</span>
              </span>
            {:else}
              <span class="meta-tag kind-other">
                <span class="meta-tag-value">{$t.anonymous}</span>
              </span>
            {/each}
            {#if sdkBadge(session)}
              <span class="meta-tag kind-sdk" title="SDK">{sdkBadge(session)}</span>
            {/if}
          </div>

          <div class="sc-bottom">
            <span class="sc-time" class:sc-time-range={isEnded(session)} title={sessionTimeLabel(session)}>
              {sessionTimeLabel(session)}
            </span>
          </div>
        </button>
      {/each}
    {/if}
  </div>

  {#if totalPages > 1}
    <div class="pagination">
      <button type="button" class="btn btn-ghost btn-page"
        onclick={() => fetchSessions(page - 1)} disabled={page === 0}>{$t.prevPage}</button>
      <span class="page-info">{$t.pageOf(page + 1, totalPages)}</span>
      <button type="button" class="btn btn-ghost btn-page"
        onclick={() => fetchSessions(page + 1)} disabled={page >= totalPages - 1}>{$t.nextPage}</button>
    </div>
  {/if}
</div>
