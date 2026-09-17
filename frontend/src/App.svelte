<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import SessionList from "./pages/SessionList.svelte";
  import SessionPlayer from "./pages/SessionPlayer.svelte";
  import SessionDetails from "./components/SessionDetails.svelte";
  import ProjectsView from "./components/ProjectsView.svelte";
  import IntegrationGuideView from "./components/IntegrationGuideView.svelte";
  import ServerConfigView from "./components/ServerConfigView.svelte";
  import UsersView from "./components/UsersView.svelte";
  import AccountView from "./components/AccountView.svelte";
  import StatsView from "./components/StatsView.svelte";
  import { t, locale } from './i18n';
  import { formatDateTime, formatRange, formatDurationMs } from './lib/dateFormat';
  import { isSessionLive, sessionActivity } from './lib/sessionLive';

  let activeNav = $state<'replay' | 'settings'>('replay');
  let settingsTab = $state<'projects' | 'guide' | 'server' | 'users' | 'account' | 'stats'>('projects');

  type Nav = 'replay' | 'settings';
  type Tab = 'projects' | 'guide' | 'server' | 'users' | 'account' | 'stats';

  function parseViewFromUrl(): { nav: Nav; tab: Tab } {
    const q = new URLSearchParams(location.search);
    const tabRaw = q.get('tab') || 'projects';
    const tab: Tab = (['projects','guide','server','users','account','stats'] as Tab[]).includes(tabRaw as Tab)
      ? tabRaw as Tab : 'projects';
    return q.get('view') === 'settings' ? { nav: 'settings', tab } : { nav: 'replay', tab };
  }

  function urlFor(nav: Nav, tab: Tab): string {
    if (nav === 'replay') return '/';
    return `/?view=settings&tab=${tab}`;
  }

  function navigate(nav: Nav, tab: Tab = settingsTab, mode: 'push' | 'replace' = 'push') {
    const nextTab = nav === 'settings' ? tab : settingsTab;
    if (mode === 'push' && nav === activeNav && (nav === 'replay' || nextTab === settingsTab)) return;
    activeNav = nav;
    if (nav === 'settings') settingsTab = nextTab;
    const state = { nav, tab: settingsTab };
    const url = urlFor(nav, settingsTab);
    if (mode === 'replace') history.replaceState(state, '', url);
    else history.pushState(state, '', url);
  }

  function onPopState(e: PopStateEvent) {
    if (e.state?.nav) {
      activeNav = e.state.nav;
      if (e.state.tab) settingsTab = e.state.tab;
    } else {
      const v = parseViewFromUrl();
      activeNav = v.nav;
      settingsTab = v.tab;
    }
  }

  let selectedSession = $state<any>(null);
  let sessionEvents = $state<any[]>([]);
  let projects = $state<any[]>([]);
  let seekFn: ((ms: number) => void) | null = null;
  let eventsLoading = $state(false);
  let toastMsg = $state('');
  let toastTimer: ReturnType<typeof setTimeout> | null = null;
  let copiedId = $state(false);
    let currentUser = $state<any>(null);
   let canManage = $derived(currentUser?.role === 'SUPER_ADMIN' || currentUser?.role === 'ADMIN');
   let recommendedSessions = $state<any[]>([]);
   let recLoading = $state(false);

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

   async function loadCurrentUser() {
     try {
       const res = await fetch('/api/me');
       if (res.ok) currentUser = await res.json();
     } catch {}
   }

   async function loadRecommended() {
     recLoading = true;
     try {
       const res = await fetch('/api/sessions/recommended?limit=6');
       if (res.ok) recommendedSessions = await res.json();
       else recommendedSessions = [];
     } catch {
       recommendedSessions = [];
     } finally {
       recLoading = false;
     }
   }

   function setDocumentLang(lang: string) {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = lang;
    }
  }

    onMount(() => {
      loadProjects();
      loadCurrentUser();
      loadRecommended();
      setDocumentLang($locale);
      const v = parseViewFromUrl();
      activeNav = v.nav;
      settingsTab = v.tab;
      history.replaceState({ nav: activeNav, tab: settingsTab }, '', urlFor(activeNav, settingsTab));
      window.addEventListener('popstate', onPopState);
      return () => window.removeEventListener('popstate', onPopState);
    });

  $effect(() => {
    setDocumentLang($locale);
  });

  onDestroy(() => {
    window.removeEventListener('popstate', onPopState);
  });

  let eventsAbort: AbortController | null = null;

  async function handleSelectSession(session: any) {
    selectedSession = session;
    sessionEvents = [];
    seekFn = null;
    navigate('replay');
    eventsLoading = true;
    eventsAbort?.abort();
    eventsAbort = new AbortController();
    const signal = eventsAbort.signal;
    try {
      const all: any[] = [];
      let afterId: number | null = null;
      let hasMore = true;
      let pages = 0;
      while (hasMore && pages < 10) {
        const params = new URLSearchParams({ paged: 'true', limit: '2000' });
        if (afterId != null) params.set('afterId', String(afterId));
        const res = await fetch(`/api/sessions/${session.sessionId}/events?${params}`, { signal });
        if (!res.ok) break;
        const page = await res.json();
        const batch = Array.isArray(page) ? page : (page.events || []);
        all.push(...batch);
        pages += 1;
        if (Array.isArray(page)) {
          hasMore = false;
        } else {
          hasMore = !!page.hasMore;
          afterId = page.nextAfterId ?? null;
          if (!hasMore || batch.length === 0) hasMore = false;
        }
      }
      if (!signal.aborted) sessionEvents = all;
    } catch (e) {
      if (!(e instanceof DOMException && e.name === 'AbortError')) {
        console.error('Failed to fetch events', e);
      }
    }
    finally { if (!signal.aborted) eventsLoading = false; }
  }

  function handlePlayerReady({ seekTo }: { seekTo: (ms: number) => void }) {
    seekFn = seekTo;
  }

  function handleSeekTo(ms: number) {
    if (seekFn) seekFn(ms);
  }

  async function handleForceStop() {
    if (!selectedSession?.sessionId) return;
    try {
      const res = await fetch(`/api/sessions/${selectedSession.sessionId}/stop`, { method: 'POST' });
      if (res.ok) {
        selectedSession = { ...selectedSession, status: 'STOPPED' };
        showToast($t.sessionStopped);
      } else {
        showToast($t.stopError);
      }
    } catch {
      showToast($t.stopError);
    }
  }

  async function handleDeleteSession() {
    if (!selectedSession?.sessionId) return;
    try {
      const res = await fetch(`/api/sessions/${selectedSession.sessionId}`, { method: 'DELETE' });
      if (res.ok) {
        showToast($t.sessionDeleted);
        sessionEvents = [];
        selectedSession = null;
      } else {
        showToast($t.deleteError);
      }
    } catch {
      showToast($t.deleteError);
    }
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
    return sessionActivity(session) === 'ended';
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
    return isSessionLive(session);
  }

  function goProjects() { navigate('settings', 'projects'); }
  function goGuide() { navigate('settings', 'guide'); }
  function goUsers() { navigate('settings', 'users'); }
</script>

<div class="app-shell">
  <header class="topbar">
    <button type="button" class="topbar-left" onclick={() => navigate('replay')} aria-label={$t.settingsMenuPlayer}>
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
      </div>
    </button>
    <div class="topbar-right">
      {#if currentUser}
        <div class="topbar-account">
          <span>{currentUser.username}</span>
           <button type="button" class="linkish" onclick={() => navigate('settings', 'account')}>{$t.changePassword}</button>
          <a href="/logout">{$t.logout}</a>
        </div>
      {/if}
      <div class="lang-select-wrap">
        <select class="lang-select" bind:value={$locale} title="Language">
          <option value="ko">한국어</option>
          <option value="en">English</option>
        </select>
      </div>
    </div>
  </header>

  <div class="dashboard" class:has-session={activeNav === 'replay' && !!selectedSession}>
    <aside class="left-panel">
      <div class="panel-nav">
        <button
          type="button"
          class="nav-tab"
          class:active={activeNav === 'replay'}
          onclick={() => navigate('replay')}
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
          onclick={() => navigate('settings', settingsTab)}
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
              onclick={() => navigate('settings', 'projects')}
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
              class:active={settingsTab === 'stats'}
              onclick={() => navigate('settings','stats')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                <line x1="12" y1="20" x2="12" y2="10"></line>
                <line x1="18" y1="20" x2="18" y2="4"></line>
                <line x1="6" y1="20" x2="6" y2="16"></line>
              </svg>
              <span class="subnav-text">
                <span class="subnav-label">{$t.settingsMenuStats}</span>
                <span class="subnav-hint">{$t.settingsStatsHint}</span>
              </span>
            </button>
            <button
              type="button"
              class="settings-subnav-item"
              class:active={settingsTab === 'guide'}
              onclick={() => navigate('settings', 'guide')}
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
            {#if canManage}
            <button
              type="button"
              class="settings-subnav-item"
              class:active={settingsTab === 'server'}
              onclick={() => navigate('settings', 'server')}
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                <rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect>
                <rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect>
                <line x1="6" y1="6" x2="6.01" y2="6"></line>
                <line x1="6" y1="18" x2="6.01" y2="18"></line>
              </svg>
              <span class="subnav-text">
                <span class="subnav-label">{$t.settingsMenuServer}</span>
                 <span class="subnav-hint">{$t.settingsServerHint}</span>
               </span>
             </button>
            {/if}
            {#if currentUser?.role === 'SUPER_ADMIN'}
             <button
               type="button"
               class="settings-subnav-item"
               class:active={settingsTab === 'users'}
               onclick={() => navigate('settings', 'users')}
             >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
               <span class="subnav-text">
                 <span class="subnav-label">{$t.settingsMenuUsers}</span>
                 <span class="subnav-hint">{$t.settingsUsersHint}</span>
               </span>
             </button>
            {/if}
              <button
               type="button"
               class="settings-subnav-item"
                class:active={settingsTab === 'account'}
                onclick={() => navigate('settings', 'account')}
             >
               <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="subnav-icon">
                 <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                 <circle cx="12" cy="7" r="4"></circle>
               </svg>
               <span class="subnav-text">
                 <span class="subnav-label">{$t.settingsMenuAccount}</span>
                 <span class="subnav-hint">{$t.settingsAccountHint}</span>
               </span>
             </button>
           </div>
          <div class="settings-rail-footer">
             <button type="button" class="btn-return-clean" onclick={() => navigate('replay')}>
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
            <ProjectsView bind:projects={projects} canManage={canManage} onClose={() => navigate('replay')} />
         {:else if settingsTab === 'stats'}
            <StatsView projects={projects} />
         {:else if settingsTab === 'server'}
           <ServerConfigView onClose={() => navigate('replay')} />
         {:else if settingsTab === 'users'}
            <UsersView onClose={() => navigate('replay')} />
         {:else if settingsTab === 'account'}
            <AccountView onClose={() => navigate('replay')} />
         {:else}
            <IntegrationGuideView {projects} canManage={canManage} onClose={() => navigate('replay')} />
         {/if}
        {:else}
        {#if recLoading || recommendedSessions.length > 0}
         <div class="rec-rail">
           <div class="rec-head">{$t.recommendedTitle}</div>
           {#if recLoading}
             <div class="rec-loading">{$t.recommendedLoading}</div>
           {:else}
             <div class="rec-list">
               {#each recommendedSessions as rec (rec.sessionId)}
                 <button type="button" class="rec-card" class:active={selectedSession?.sessionId === rec.sessionId} onclick={() => handleSelectSession(rec)}>
                   <span class="rec-card-title">{projectTitle(rec)}</span>
                   {#if rec.hasError}
                     <span class="ended-meta-badge">{$t.recommendedReasonError}</span>
                   {/if}
                   <span class="rec-card-user">{rec.userId || $t.anonymous}</span>
                 </button>
               {/each}
             </div>
           {/if}
         </div>
        {/if}
          {#if selectedSession}
           <div class="session-bar">
             <div class="session-bar-left">
               <span class="session-bar-user">{selectedSession.userId || $t.anonymous}</span>
               <span class="session-bar-title">
                 {projectTitle(selectedSession)}
               </span>
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

           <div class="player-stack">
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
           </div>
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
                <button type="button" class="btn-secondary" onclick={goProjects}>{$t.emptyCtaProjects}</button>
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
           onForceStop={handleForceStop}
           onDeleteSession={handleDeleteSession}
           loading={eventsLoading}
           canManage={canManage}
         />
      </aside>
    {/if}
  </div>
</div>

{#if toastMsg}
  <div class="toast" role="status">{toastMsg}</div>
{/if}


