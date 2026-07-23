<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Replayer } from 'rrweb';
  import 'rrweb/dist/style.css';

  // Svelte 5 props
  let { id, navigateTo } = $props();

  let playerContainer: HTMLElement | null = $state(null);
  let replayer: any = null;
  let eventSource: EventSource | null = null;
  let events: any[] = [];
  let loading = $state(true);
  let error = $state('');

  onMount(async () => {
    try {
      const rawEvents: any[] = [];
      let afterId: number | null = null;
      let hasMore = true;
      while (hasMore) {
        const params = new URLSearchParams({ paged: 'true', limit: '2000' });
        if (afterId != null) params.set('afterId', String(afterId));
        const res = await fetch(`/api/sessions/${id}/events?${params}`);
        if (!res.ok) break;
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
      events = rawEvents;
      
      loading = false;

      setTimeout(() => {
        if (playerContainer) {
          replayer = new Replayer(events, {
            root: playerContainer,
            liveMode: true,
          });
          replayer.play();

          eventSource = new EventSource(`/api/sessions/${id}/live`);
          
          eventSource.addEventListener('message', (e) => {
            try {
              const parsed = JSON.parse(e.data);
              const batch = Array.isArray(parsed) ? parsed : [parsed];
              for (const evt of batch) {
                replayer.addEvent(evt);
              }
            } catch (err) {
              console.error('Failed to parse live event', err);
            }
          });

          eventSource.addEventListener('connected', (e) => {
            console.log(e.data);
          });
          
          eventSource.onerror = (e) => {
            console.error('SSE Error', e);
            error = 'Live connection lost.';
            if (eventSource) eventSource.close();
          };
        }
      }, 50);

    } catch (e) {
      error = 'Failed to initialize live player.';
      loading = false;
    }
  });

  onDestroy(() => {
    if (eventSource) {
      eventSource.close();
    }
    if (replayer) {
      replayer.pause();
    }
  });
</script>

<div class="glass-panel">
  <div class="header">
    <h2>Live Tailing: {id} <span class="badge active">LIVE</span></h2>
    <button class="btn" onclick={() => navigateTo('list')}>Back to List</button>
  </div>

  {#if loading}
    <p>Connecting to live session...</p>
  {:else if error}
    <p style="color: #ef4444;">{error}</p>
  {/if}
  
  <div class="player-container" bind:this={playerContainer}></div>
</div>
