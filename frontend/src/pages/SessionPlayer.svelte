<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Replayer } from 'rrweb';
  import 'rrweb/dist/style.css';
  import { t } from '../i18n';

  let { session, events = null, onPlayerReady = null } = $props();

  let playerContainer: HTMLElement | null = $state(null);
  let playerViewport: HTMLElement | null = $state(null);
  let replayer: any = null;
  let eventSource: EventSource | null = null;
  let dialogOverlayTimer: ReturnType<typeof setTimeout> | null = null;

  let loading = $state(true);
  let errorKey = $state('');

  // Playback state
  let isPlaying = $state(false);
  let currentTime = $state(0);   // ms offset from recording start
  let totalTime = $state(0);     // total duration ms
  let speed = $state(1);
  let skipInactive = $state(false);
  let isFullscreen = $state(false);
  let rafId: number | null = null;
  let isLive = $state(false);
  let liveDisconnected = $state(false);

  // Live: follow the growing edge (within this ms of totalTime)
  const LIVE_EDGE_MS = 1200;
  let followLiveEdge = $state(true);

  // Prevent time jumping during seek/drag transitions
  let isSeeking = $state(false);
  let seekTimeoutId: any = null;
  let isDragging = $state(false);
  let didDragSeek = false;

  // ── Formatting ────────────────────────────────────────────────
  function fmt(ms: number): string {
    if (!isFinite(ms) || ms < 0) ms = 0;
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  }

  function refreshMetaDuration() {
    if (!replayer) return;
    try {
      const meta = replayer.getMetaData();
      if (meta?.totalTime != null && isFinite(meta.totalTime) && meta.totalTime >= 0) {
        totalTime = Math.max(totalTime, meta.totalTime);
      }
    } catch {}
  }

  function readReplayerTime(): number {
    if (!replayer) return currentTime;
    try {
      const tVal = replayer.getCurrentTime();
      if (typeof tVal === 'number' && isFinite(tVal) && tVal >= 0) {
        return tVal;
      }
    } catch {}
    return currentTime;
  }

  // ── RAF tick: sync progress from rrweb clock only ───────────────
  function tick() {
    if (replayer && !isSeeking && !isDragging) {
      try {
        refreshMetaDuration();
        if (isPlaying || isLive) {
          const tVal = readReplayerTime();
          const cap = totalTime > 0 ? totalTime : tVal;
          currentTime = Math.max(0, Math.min(tVal, cap || tVal));

          // Live edge-follow: if user is near the end, stick to newest duration
          if (isLive && followLiveEdge && isPlaying && totalTime > 0) {
            if (currentTime >= totalTime - LIVE_EDGE_MS) {
              currentTime = totalTime;
            }
          }
        }
      } catch {}
    }
    rafId = requestAnimationFrame(tick);
  }

  function stopRaf() {
    if (rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
  }

  // ── Controls ────────────────────────────────────────────────────
  function togglePlay() {
    if (!replayer) return;
    if (isPlaying) {
      replayer.pause();
      isPlaying = false;
      if (isLive) followLiveEdge = false;
    } else {
      refreshMetaDuration();
      let startFrom = currentTime;
      if (isLive) {
        // Resume at live edge when already at/near end
        if (totalTime > 0 && currentTime >= totalTime - LIVE_EDGE_MS) {
          startFrom = Math.max(0, totalTime);
          followLiveEdge = true;
        }
      } else if (totalTime > 0 && currentTime >= totalTime) {
        startFrom = 0;
        currentTime = 0;
      }
      replayer.play(startFrom);
      isPlaying = true;
    }
  }

  function setSpeed(s: number) {
    speed = s;
    if (replayer) {
      replayer.setConfig({ speed: s });
    }
  }

  // Handle skip inactive toggle
  function toggleSkipInactive() {
    skipInactive = !skipInactive;
    if (replayer) {
      replayer.setConfig({ skipInactive });
    }
  }

  function seekTo(ms: number) {
    if (!replayer) return;
    refreshMetaDuration();
    const maxT = Math.max(totalTime, 0);
    const target = Math.max(0, Math.min(ms, maxT || ms));

    isSeeking = true;
    if (seekTimeoutId) clearTimeout(seekTimeoutId);

    currentTime = target;
    if (isLive) {
      followLiveEdge = maxT > 0 ? target >= maxT - LIVE_EDGE_MS : true;
    }

    try {
      replayer.pause();
    } catch {}
    replayer.play(target);
    isPlaying = true;

    seekTimeoutId = setTimeout(() => {
      isSeeking = false;
      currentTime = readReplayerTime();
      refreshMetaDuration();
    }, 150);
  }

  function ratioFromEvent(e: MouseEvent, el: HTMLElement): number {
    const rect = el.getBoundingClientRect();
    return Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  }

  function handleProgressClick(e: MouseEvent) {
    if (!replayer || totalTime <= 0 || didDragSeek) {
      didDragSeek = false;
      return;
    }
    const ratio = ratioFromEvent(e, e.currentTarget as HTMLElement);
    seekTo(Math.floor(ratio * totalTime));
  }

  function handleProgressPointerDown(e: PointerEvent) {
    if (!replayer || totalTime <= 0) return;
    e.preventDefault();
    const wrap = e.currentTarget as HTMLElement;
    isDragging = true;
    isSeeking = true;
    didDragSeek = false;
    wrap.setPointerCapture?.(e.pointerId);

    const apply = (ev: PointerEvent | MouseEvent) => {
      const ratio = ratioFromEvent(ev as MouseEvent, wrap);
      currentTime = Math.floor(ratio * totalTime);
    };
    apply(e);

    const onMove = (ev: PointerEvent) => {
      if (!isDragging) return;
      didDragSeek = true;
      apply(ev);
    };
    const onUp = (ev: PointerEvent) => {
      if (!isDragging) return;
      isDragging = false;
      apply(ev);
      seekTo(currentTime);
      didDragSeek = true;
      wrap.releasePointerCapture?.(ev.pointerId);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
  }

  function handleProgressKeydown(e: KeyboardEvent) {
    if (!replayer || totalTime === 0) return;
    if (e.key === 'ArrowLeft') seekTo(Math.max(0, currentTime - 5000));
    if (e.key === 'ArrowRight') seekTo(Math.min(totalTime, currentTime + 5000));
  }

  function skipBack() { seekTo(Math.max(0, currentTime - 10000)); }
  function skipFwd()  { seekTo(Math.min(totalTime, currentTime + 10000)); }

  function toggleFullscreen() {
    const el = playerContainer?.closest('.center-panel') as HTMLElement | null;
    if (!el) return;
    if (!document.fullscreenElement) {
      el.requestFullscreen().then(() => isFullscreen = true).catch(() => {});
    } else {
      document.exitFullscreen().then(() => isFullscreen = false).catch(() => {});
    }
  }

  function handleSpaceKey(e: KeyboardEvent) {
    if (e.code !== 'Space' && e.key !== ' ') return;
    const tag = (e.target as HTMLElement)?.tagName?.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return;
    if ((e.target as HTMLElement)?.isContentEditable) return;
    e.preventDefault();
    togglePlay();
  }

  function parseRawEvents(list: any[]): any[] {
    const rawEvents: any[] = [];
    for (const e of list) {
      try {
        if (e == null) continue;
        if (typeof e.type === 'number' && e.timestamp != null) {
          rawEvents.push(e);
          continue;
        }
        const data = typeof e.eventData === 'string' ? JSON.parse(e.eventData) : e.eventData;
        if (data) rawEvents.push(data);
      } catch {}
    }
    return rawEvents;
  }

  async function loadAllEvents(sessionId: string): Promise<any[]> {
    const rawEvents: any[] = [];
    let afterId: number | null = null;
    let hasMore = true;
    while (hasMore) {
      const params = new URLSearchParams({ paged: 'true', limit: '2000' });
      if (afterId != null) params.set('afterId', String(afterId));
      const res = await fetch(`/api/sessions/${sessionId}/events?${params}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const page = await res.json();
      const batch = Array.isArray(page) ? page : (page.events || []);
      for (const e of batch) {
        rawEvents.push(typeof e.eventData === 'string' ? JSON.parse(e.eventData) : e.eventData);
      }
      if (Array.isArray(page)) {
        hasMore = false;
      } else {
        hasMore = !!page.hasMore;
        afterId = page.nextAfterId ?? null;
        if (!hasMore || batch.length === 0) hasMore = false;
      }
    }
    return rawEvents;
  }

  function showDialogOverlay(tag: string, payload: any, phase?: string) {
    const host = playerViewport || playerContainer?.closest('.player-viewport') as HTMLElement | null;
    if (!host) return;

    // Close events dismiss immediately
    if (phase === 'close') {
      const existing = host.querySelector('.sl-replay-dialog');
      if (existing) existing.remove();
      if (dialogOverlayTimer) {
        clearTimeout(dialogOverlayTimer);
        dialogOverlayTimer = null;
      }
      return;
    }

    const existing = host.querySelector('.sl-replay-dialog');
    if (existing) existing.remove();
    if (dialogOverlayTimer) {
      clearTimeout(dialogOverlayTimer);
      dialogOverlayTimer = null;
    }

    const message = payload?.message != null ? String(payload.message) : '';
    const result = payload?.result;
    const defaultValue = payload?.defaultValue != null ? String(payload.defaultValue) : '';
    const hostTitle = (() => {
      try {
        return session?.source ? String(session.source).replace(/^\//, '') || 'session' : 'session';
      } catch {
        return 'session';
      }
    })();

    const overlay = document.createElement('div');
    overlay.className = 'sl-replay-dialog';
    overlay.setAttribute('data-tag', tag);

    const modal = document.createElement('div');
    modal.className = 'sl-replay-dialog-modal';

    const title = document.createElement('div');
    title.className = 'sl-replay-dialog-title';
    title.textContent = (hostTitle || 'This page') + ' says';

    const body = document.createElement('div');
    body.className = 'sl-replay-dialog-body';
    body.textContent = message;

    modal.appendChild(title);
    modal.appendChild(body);

    if (tag === 'PROMPT') {
      const input = document.createElement('div');
      input.className = 'sl-replay-dialog-input';
      input.textContent =
        result != null && phase !== 'open' ? String(result) : defaultValue;
      modal.appendChild(input);
    }

    const footer = document.createElement('div');
    footer.className = 'sl-replay-dialog-footer';

    if (tag === 'CONFIRM' || tag === 'PROMPT') {
      const cancel = document.createElement('span');
      cancel.className = 'sl-replay-dialog-btn sl-replay-dialog-btn-secondary';
      cancel.textContent = 'Cancel';
      if (typeof result === 'boolean' && result === false) {
        cancel.classList.add('sl-replay-dialog-btn-active');
      }
      footer.appendChild(cancel);
    }

    const ok = document.createElement('span');
    ok.className = 'sl-replay-dialog-btn sl-replay-dialog-btn-primary';
    ok.textContent = 'OK';
    if (result === true || (tag === 'ALERT') || (tag === 'PROMPT' && result != null && result !== false)) {
      ok.classList.add('sl-replay-dialog-btn-active');
    }
    footer.appendChild(ok);
    modal.appendChild(footer);
    overlay.appendChild(modal);
    host.appendChild(overlay);

    // Keep open until close event, with fallback timeout
    dialogOverlayTimer = setTimeout(() => {
      overlay.remove();
      dialogOverlayTimer = null;
    }, 8000);
  }

  function handleCustomEvent(e: any) {
    if (e?.type === 5 && e.data?.tag && ['ALERT', 'CONFIRM', 'PROMPT'].includes(e.data.tag)) {
      const payload = e.data.payload || {};
      showDialogOverlay(e.data.tag, payload, payload.phase);
    }
  }

  // ── Mount ────────────────────────────────────────────────────────
  onMount(async () => {
    window.addEventListener('keydown', handleSpaceKey);

    try {
      let rawEvents: any[];
      if (events != null && Array.isArray(events)) {
        rawEvents = parseRawEvents(events);
      } else {
        rawEvents = await loadAllEvents(session.sessionId);
      }

      loading = false;

      if (rawEvents.length === 0) {
        errorKey = 'noEvents';
        return;
      }

      await new Promise<void>(r => setTimeout(r, 60));
      if (!playerContainer) return;

      isLive = session.status === 'ACTIVE';

      replayer = new Replayer(rawEvents, {
        root: playerContainer,
        liveMode: isLive,
        speed,
        skipInactive,
        showWarning: false,
        showDebug: false,
      });

      refreshMetaDuration();
      followLiveEdge = isLive;

      replayer.on('start', () => {
        isPlaying = true;
      });

      replayer.on('pause', () => {
        isPlaying = false;
      });

      replayer.on('finish', () => {
        if (!isLive) {
          isPlaying = false;
          currentTime = totalTime;
        } else if (followLiveEdge) {
          // Stay ready for next live chunk — do not reset progress to 0
          isPlaying = true;
          refreshMetaDuration();
          try {
            const t = totalTime > 0 ? Math.max(0, totalTime - 1) : 0;
            replayer.play(t);
          } catch {}
        }
      });

      replayer.on('event-cast', (e: any) => {
        handleCustomEvent(e);
      });

      if (onPlayerReady) {
        onPlayerReady({ seekTo });
      }

      rafId = requestAnimationFrame(tick);

      if (isLive) {
        // Start at live edge (end of buffered events), not from t=0 wall-clock
        refreshMetaDuration();
        const startAt = totalTime > 0 ? Math.max(0, totalTime - 50) : 0;
        currentTime = startAt;
        followLiveEdge = true;
        try {
          replayer.play(startAt);
        } catch {
          replayer.play();
        }
        isPlaying = true;

        eventSource = new EventSource(`/api/sessions/${session.sessionId}/live`);
        eventSource.addEventListener('message', (ev) => {
          try {
            const parsed = JSON.parse(ev.data);
            const batch = Array.isArray(parsed) ? parsed : [parsed];
            for (const evt of batch) {
              replayer.addEvent(evt);
              handleCustomEvent(evt);
            }
            const prevTotal = totalTime;
            refreshMetaDuration();
            liveDisconnected = false;

            // Keep playback attached to the growing timeline
            if (followLiveEdge && isPlaying && totalTime > prevTotal) {
              try {
                const t = readReplayerTime();
                if (t < totalTime - LIVE_EDGE_MS) {
                  replayer.play(Math.max(0, totalTime - 30));
                }
              } catch {}
            }
          } catch {}
        });
        eventSource.onerror = () => {
          liveDisconnected = true;
          if (eventSource) {
            eventSource.close();
            eventSource = null;
          }
        };
      }

    } catch (e) {
      console.error(e);
      errorKey = 'errorLoad';
      loading = false;
    }
  });

  onDestroy(() => {
    window.removeEventListener('keydown', handleSpaceKey);
    stopRaf();
    if (seekTimeoutId) clearTimeout(seekTimeoutId);
    if (dialogOverlayTimer) clearTimeout(dialogOverlayTimer);
    if (eventSource) eventSource.close();
    try {
      replayer?.pause();
    } catch (e) {}
  });

  let progressPct = $derived(
    totalTime > 0
      ? Math.min(100, Math.max(0, (currentTime / totalTime) * 100))
      : isLive
        ? 100
        : 0
  );
</script>

<!-- Player viewport -->
<div class="player-viewport" bind:this={playerViewport}>
  {#if loading}
    <div class="player-empty-clean">
      <div class="spinner-clean"></div>
      <h3>{$t.loading}</h3>
    </div>
  {:else if errorKey}
    <div class="player-empty-clean">
      <div class="error-icon-soft" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" style="width:28px;height:28px">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
      </div>
      <h3>{errorKey === 'noEvents' ? $t.noEvents : $t.errorLoad}</h3>
    </div>
  {:else}
    <div class="player-inner" bind:this={playerContainer}></div>
  {/if}
</div>

<!-- Controls bar — always shown when not loading/error -->
{#if !loading && !errorKey}
{#if liveDisconnected}
  <div class="live-disconnect-banner" role="status">{$t.liveDisconnected}</div>
{/if}
<div class="player-controls">

  <!-- Progress row -->
  <div class="progress-row">
    <span class="time-label">{fmt(currentTime)}</span>

    <!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
    <div
      class="progress-bar-wrap"
      class:dragging={isDragging}
      role="slider"
      aria-label="Playback position"
      aria-valuenow={Math.floor(currentTime)}
      aria-valuemin={0}
      aria-valuemax={Math.floor(totalTime)}
      tabindex="0"
      onclick={handleProgressClick}
      onpointerdown={handleProgressPointerDown}
      onkeydown={handleProgressKeydown}
    >
      <div class="progress-bar-fill" style="width: {progressPct}%">
        <div class="progress-thumb"></div>
      </div>
    </div>

    <span class="time-label" style="text-align:right">{fmt(totalTime)}</span>
  </div>

  <!-- Controls row -->
  <div class="controls-row">
    <div class="controls-left">

      <!-- Back 10 s -->
      <button class="ctrl-btn" title={$t.back10} onclick={skipBack}>-10s</button>

      <!-- Play / Pause -->
      <button class="play-btn-clean" onclick={togglePlay} title={isPlaying ? $t.playing : $t.paused}>
        {#if isPlaying}
          <svg viewBox="0 0 24 24" fill="currentColor" class="icon-play-ctrl" style="width:14px;height:14px;margin-top:1px"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>
        {:else}
          <svg viewBox="0 0 24 24" fill="currentColor" class="icon-play-ctrl" style="width:14px;height:14px;margin-left:2px;margin-top:1px"><path d="M8 5v14l11-7z"></path></svg>
        {/if}
      </button>

      <!-- Forward 10 s -->
      <button class="ctrl-btn" title={$t.fwd10} onclick={skipFwd}>+10s</button>

      <!-- Speed -->
      <div class="speed-btns">
        {#each [1, 2, 4] as s}
          <button class="speed-btn" class:active={speed === s} onclick={() => setSpeed(s)}>{s}x</button>
        {/each}
      </div>

      <!-- Skip inactive toggle -->
      <button
        class="skip-toggle"
        onclick={toggleSkipInactive}
        title={$t.skipInactive}
        aria-pressed={skipInactive}
        style="background:none;border:none;cursor:pointer;display:flex;align-items:center;gap:0.4rem;padding:0"
      >
        <div class="toggle-switch" class:on={skipInactive}><div class="toggle-knob"></div></div>
        <span style="font-size:0.75rem;color:var(--muted)">{$t.skipInactive}</span>
      </button>

    </div>

    <div class="controls-right">
      {#if isLive}
        <span class="live-chip" class:disconnected={liveDisconnected}>LIVE</span>
      {/if}
      <!-- Fullscreen -->
      <button class="ctrl-btn btn-fullscreen-clean" title={$t.fullscreen} onclick={toggleFullscreen}>
        {#if isFullscreen}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-fs" style="width:14px;height:14px"><path d="M4 14h6v6M20 10h-6V4M14 10l7-7M10 14l-7 7"></path></svg>
        {:else}
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-fs" style="width:14px;height:14px"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"></path></svg>
        {/if}
      </button>
    </div>
  </div>

</div>
{/if}

<style>
  .error-icon-soft {
    color: var(--danger);
    opacity: 0.85;
    margin-bottom: 0.35rem;
  }

  .live-disconnect-banner {
    background: rgba(245, 158, 11, 0.12);
    border-top: 1px solid rgba(245, 158, 11, 0.28);
    color: #f59e0b;
    font-size: 0.72rem;
    font-weight: 600;
    text-align: center;
    padding: 0.35rem 1rem;
    letter-spacing: 0.01em;
  }

  .live-chip {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
    font-size: 0.65rem;
    font-weight: 800;
    letter-spacing: 0.06em;
    color: #10b981;
    background: rgba(16, 185, 129, 0.12);
    border: 1px solid rgba(16, 185, 129, 0.35);
    border-radius: 999px;
    padding: 0.2rem 0.55rem;
  }

  .live-chip::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #10b981;
    box-shadow: 0 0 6px #10b98188;
    animation: live-pulse 1.4s ease-in-out infinite;
  }

  .live-chip.disconnected {
    color: #f59e0b;
    background: rgba(245, 158, 11, 0.12);
    border-color: rgba(245, 158, 11, 0.35);
  }

  .live-chip.disconnected::before {
    background: #f59e0b;
    box-shadow: none;
    animation: none;
  }

  @keyframes live-pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.35; }
  }

  .controls-right {
    display: flex;
    align-items: center;
    gap: 0.65rem;
  }

  :global(.progress-bar-wrap.dragging) {
    cursor: grabbing;
  }

  :global(.progress-bar-wrap.dragging .progress-thumb) {
    opacity: 1;
  }

  :global(.player-viewport) {
    position: relative;
  }

  :global(.sl-replay-dialog) {
    position: absolute;
    inset: 0;
    z-index: 50;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.32);
    pointer-events: none;
    font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
  }

  :global(.sl-replay-dialog-modal) {
    background: #fff;
    color: #202124;
    border-radius: 8px;
    box-shadow:
      0 1px 2px rgba(60, 64, 67, 0.3),
      0 2px 6px 2px rgba(60, 64, 67, 0.15);
    width: min(344px, 86%);
    padding: 20px 20px 16px;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    animation: sl-dialog-in 0.14s ease-out;
  }

  :global(.sl-replay-dialog-title) {
    font-size: 15px;
    font-weight: 400;
    color: #202124;
    line-height: 1.4;
    margin: 0 0 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :global(.sl-replay-dialog-body) {
    font-size: 13px;
    color: #3c4043;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    margin: 0 0 4px;
  }

  :global(.sl-replay-dialog-input) {
    margin-top: 12px;
    padding: 8px 10px;
    border: 1px solid #dadce0;
    border-radius: 4px;
    font-size: 13px;
    color: #202124;
    background: #fff;
    min-height: 1.2em;
    white-space: pre-wrap;
    word-break: break-word;
  }

  :global(.sl-replay-dialog-footer) {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
    margin-top: 18px;
  }

  :global(.sl-replay-dialog-btn) {
    border-radius: 4px;
    padding: 8px 16px;
    font-size: 13px;
    font-weight: 500;
    min-width: 64px;
    text-align: center;
    box-sizing: border-box;
  }

  :global(.sl-replay-dialog-btn-primary) {
    background: #1a73e8;
    color: #fff;
    border: 1px solid transparent;
  }

  :global(.sl-replay-dialog-btn-secondary) {
    background: #fff;
    color: #1a73e8;
    border: 1px solid #dadce0;
  }

  :global(.sl-replay-dialog-btn-active) {
    box-shadow: 0 0 0 2px rgba(26, 115, 232, 0.35);
  }

  @keyframes sl-dialog-in {
    from { opacity: 0; transform: scale(0.96) translateY(6px); }
    to { opacity: 1; transform: scale(1) translateY(0); }
  }

  :global(.progress-bar-wrap) {
    cursor: pointer;
  }

  :global(.live-edge-hint) {
    font-size: 0.65rem;
    color: var(--muted);
    margin-left: 0.25rem;
  }
</style>
