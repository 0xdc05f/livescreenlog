<script lang="ts">
  import { onMount } from 'svelte';
  import SessionList from "./pages/SessionList.svelte";
  import SessionPlayer from "./pages/SessionPlayer.svelte";
  import SessionDetails from "./components/SessionDetails.svelte";
  import ProjectsView from "./components/ProjectsView.svelte";
  import IntegrationGuideView from "./components/IntegrationGuideView.svelte";
  import { t, locale } from './i18n';
  import { formatDateTime, formatRange, formatDurationMs } from './lib/dateFormat';

  let activeNav = $state<'replay' | 'settings'>('replay');
  let settingsTab = $state<'projects' | 'guide'>('projects');

  let selectedSession = $state<any>(null);
  let sessionEvents = $state<any[]>([]);
  let projects = $state<any[]>([]);
  let seekFn: ((ms: number) => void) | null = null;
  let eventsLoading = $state(false);
  let toastMsg = $state('');
  let toastTimer: ReturnType<typeof setTimeout> | null = null;
  let copiedId = $state(false);

  function showToast(msg: string) {
    toastMsg = msg;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastMsg = ''; }, 1800);
  }

  async function loadProjects() {
    try {
      const res = await fetch('/api/projects');
      if (res.ok) projects = await res.json();
    } catch {}
  }

  function setDocumentLang(lang: string) {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = lang;
    }
  }

  onMount(() => {
    loadProjects();
    setDocumentLang($locale);
  });

  $effect(() => {
    setDocumentLang($locale);
  });

  async function handleSelectSession(session: any) {
    selectedSession = session;
    sessionEvents = [];
    seekFn = null;
    activeNav = 'replay';
    eventsLoading = true;
    try {
      const all: any[] = [];
      let afterId: number | null = null;
      let hasMore = true;
      while (hasMore) {
        const params = new URLSearchParams({ paged: 'true', limit: '2000' });
        if (afterId != null) params.set('afterId', String(afterId));
        const res = await fetch(`/api/sessions/${session.sessionId}/events?${params}`);
        if (!res.ok) break;
        const page = await res.json();
        const batch = Array.isArray(page) ? page : (page.events || []);
        all.push(...batch);
        if (Array.isArray(page)) {
          hasMore = false;
        } else {
          hasMore = !!page.hasMore;
          afterId = page.nextAfterId ?? null;
          if (!hasMore || batch.length === 0) hasMore = false;
        }
      }
      sessionEvents = all;
    } catch (e) { console.error('Failed to fetch events', e); }
    finally { eventsLoading = false; }
  }

  function handlePlayerReady({ seekTo }: { seekTo: (ms: number) => void }) {
    seekFn = seekTo;
  }

  function handleSeekTo(ms: number) {
    if (seekFn) seekFn(ms);
  }

  function shortId(id: string): string {
    if (!id) return '';
    return id.length > 10 ? id.slice(0, 8) + '…' : id;
  }

  function projectTitle(session: any): string {
    if (!session) return $t.noSystem;
    if (session.projectName) return session.projectName;
    const p = projects.find((x: any) => x.apiKey === session.projectKey);
    return p?.name ?? $t.noSystem;
  }

  function isEndedSession(session: any): boolean {
    if (!session) return false;
    if (session.status === 'STOPPED') return true;
    const diffMin = (Date.now() - new Date(session.updatedAt).getTime()) / 60000;
    return diffMin >= 30;
  }

  function sessionBarTime(session: any): string {
    if (!session?.createdAt) return '';
    if (isEndedSession(session)) {
      const endIso = session.endAt || session.updatedAt;
      const range = formatRange(session.createdAt, endIso);
      const start = new Date(session.createdAt).getTime();
      const end = endIso ? new Date(endIso).getTime() : NaN;
      const dur = Number.isFinite(end - start) ? formatDurationMs(end - start) : '';
      return dur ? `${range}  (${dur})` : range;
    }
    return formatDateTime(session.createdAt);
  }

  async function copySessionId() {
    if (!selectedSession?.sessionId) return;
    try {
      await navigator.clipboard.writeText(selectedSession.sessionId);
      copiedId = true;
      showToast($t.copied);
      setTimeout(() => { copiedId = false; }, 1500);
    } catch {}
  }

  function isLive(session: any): boolean {
    if (!session || session.status === 'STOPPED') return false;
    const diffMin = (Date.now() - new Date(session.updatedAt).getTime()) / 60000;
    return diffMin < 2;
  }

  function goProjects() {
    activeNav = 'settings';
    settingsTab = 'projects';
  }

  function goGuide() {
    activeNav = 'settings';
    settingsTab = 'guide';
  }
</script>

<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="">
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap" rel="stylesheet">

<div class="app-shell">
  <header class="topbar">
    <div class="topbar-left">
      <div class="logo-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="3.5" y="5" width="17" height="12" rx="1.5" stroke="currentColor" stroke-width="1.6"/>
          <path d="M8 21h8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          <path d="M12 17v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          <path d="M7 9.5h6.5M7 12.5h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <circle cx="17" cy="9.5" r="1.35" fill="currentColor"/>
        </svg>
      </div>
      <div class="brand-block">
        <div class="app-title">{$t.appTitle}</div>
        <div class="app-subtitle">{$t.appSubtitle}</div>
      </div>
    </div>
    <div class="topbar-right">
      <div class="lang-select-wrap">
        <select class="lang-select" bind:value={$locale} title="Language">
          <option value="ko">한국어</option>
          <option value="en">English</option>
        </select>
      </div>
    </div>
  </header>

  <div class="dashboard">
    <aside class="left-panel">
      <div class="panel-nav">
        <button
          type="button"
          class="nav-tab"
          class:active={activeNav === 'replay'}
          onclick={() => activeNav = 'replay'}
        >
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="nav-icon">
            <polygon points="5 3 19 12 5 21 5 3"></polygon>
          </svg>
          {$t.settingsMenuPlayer}
        </button>

        <button
          type="button"
          class="nav-tab"
          class:active={activeNav === 'settings'}
          onclick={() => activeNav = 'settings'}
        >
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="nav-icon">
            <circle cx="12" cy="12" r="3"></circle>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
          </svg>
          {$t.settingsTitle}
        </button>
      </div>

      {#if activeNav === 'replay'}
        <SessionList
          onSelect={handleSelectSession}
          selectedSessionId={selectedSession?.sessionId}
          {projects}
        />
      {:else}
        <div class="settings-rail">
          <div class="settings-subnav">
            <button
              type="button"
              class="settings-subnav-item"
              class:active={settingsTab === 'projects'}
              onclick={() => settingsTab = 'projects'}
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                <rect x="3" y="3" width="7" height="7"></rect>
                <rect x="14" y="3" width="7" height="7"></rect>
                <rect x="14" y="14" width="7" height="7"></rect>
                <rect x="3" y="14" width="7" height="7"></rect>
              </svg>
              <span class="subnav-text">
                <span class="subnav-label">{$t.settingsMenuProjects}</span>
                <span class="subnav-hint">{$t.settingsProjectsHint}</span>
              </span>
            </button>
            <button
              type="button"
              class="settings-subnav-item"
              class:active={settingsTab === 'guide'}
              onclick={() => settingsTab = 'guide'}
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                <polyline points="16 18 22 12 16 6"></polyline>
                <polyline points="8 6 2 12 8 18"></polyline>
              </svg>
              <span class="subnav-text">
                <span class="subnav-label">{$t.settingsMenuGuide}</span>
                <span class="subnav-hint">{$t.settingsGuideHint}</span>
              </span>
            </button>
          </div>
          <div class="settings-rail-footer">
            <button type="button" class="btn-return-clean" onclick={() => activeNav = 'replay'}>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-back">
                <line x1="19" y1="12" x2="5" y2="12"></line>
                <polyline points="12 19 5 12 12 5"></polyline>
              </svg>
              {$t.settingsMenuReturn}
            </button>
          </div>
        </div>
      {/if}
    </aside>

    <main class="center-panel">
      {#if activeNav === 'settings'}
        {#if settingsTab === 'projects'}
          <ProjectsView bind:projects={projects} onClose={() => { activeNav = 'replay'; }} />
        {:else}
          <IntegrationGuideView {projects} onClose={() => { activeNav = 'replay'; }} />
        {/if}
      {:else}
        {#if selectedSession}
          <div class="session-bar">
            <div class="session-bar-left">
              <span class="session-bar-title">
                {projectTitle(selectedSession)}
              </span>
              <span class="session-bar-user">{selectedSession.userId || $t.anonymous}</span>
            </div>
            <div class="session-bar-meta">
              <span class="session-bar-time" title={sessionBarTime(selectedSession)}>
                {sessionBarTime(selectedSession)}
              </span>
              {#if isLive(selectedSession)}
                <span class="live-meta-badge live-pulse">
                  <span class="live-dot"></span>
                  {$t.statusLive}
                </span>
              {:else if selectedSession.status === 'ACTIVE' && !isEndedSession(selectedSession)}
                <span class="idle-meta-badge">{$t.statusIdle}</span>
              {:else if isEndedSession(selectedSession)}
                <span class="ended-meta-badge">{$t.statusEnded}</span>
              {/if}
              <button
                type="button"
                class="copy-id-btn"
                class:copied={copiedId}
                onclick={copySessionId}
                title={$t.copySessionId}
              >
                <span class="copy-id-mono">{shortId(selectedSession.sessionId)}</span>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="copy-icon">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                </svg>
              </button>
            </div>
          </div>

          {#key selectedSession.sessionId}
            {#if !eventsLoading}
              <SessionPlayer
                session={selectedSession}
                events={sessionEvents}
                onPlayerReady={handlePlayerReady}
              />
            {:else}
              <div class="player-empty">
                <div class="spinner-clean"></div>
                <h3>{$t.loading}</h3>
              </div>
            {/if}
          {/key}
        {:else}
          <div class="player-empty">
            <div class="empty-icon-box">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none" class="empty-play-icon" aria-hidden="true">
                <rect x="6" y="10" width="36" height="24" rx="2" stroke="currentColor" stroke-width="1.75"/>
                <path d="M18 42h12M24 34v8" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                <path d="M14 18h14M14 24h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" opacity="0.7"/>
                <circle cx="34" cy="18" r="2.2" fill="currentColor"/>
              </svg>
            </div>
            <h3>{$t.noSessionSelected}</h3>
            <p>{$t.noSessionDesc}</p>
            <div class="empty-cta-row">
              <button type="button" class="btn-primary" onclick={goProjects}>{$t.emptyCtaProjects}</button>
              <button type="button" class="btn-secondary" onclick={goGuide}>{$t.emptyCtaGuide}</button>
            </div>
          </div>
        {/if}
      {/if}
    </main>

    {#if activeNav === 'replay'}
      <aside class="right-panel">
        <SessionDetails
          session={selectedSession}
          events={sessionEvents}
          onSeekTo={handleSeekTo}
          loading={eventsLoading}
        />
      </aside>
    {/if}
  </div>
</div>

{#if toastMsg}
  <div class="toast" role="status">{toastMsg}</div>
{/if}


