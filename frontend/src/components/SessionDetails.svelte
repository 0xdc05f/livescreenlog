<script lang="ts">
  import { t } from '../i18n';
  import { buildSessionTags, formatSdkBadge, parseUserAgent } from '../lib/uaParse';
  import { formatDateTime, formatRange, formatDurationMs } from '../lib/dateFormat';
  import DeviceIcons from './DeviceIcons.svelte';

  let { session, events, onSeekTo, loading = false } = $props();

  let activeTab = $state('activity');
  let activityFilter = $state('clicks');
  let highlightedIndex = $state(-1);
  let copiedId = $state(false);

  const ACTIVITY_CAP = 500;

  let parsedEvents = $derived.by(() => {
    if (!events || events.length === 0) return [];
    return events.map((e: any, idx: number) => {
      let parsed: any = {};
      try {
        parsed = typeof e.eventData === 'string' ? JSON.parse(e.eventData) : e.eventData;
      } catch {}

      const ts = e.timestamp;
      let category = 'other';
      let tagLabel = 'EVT';
      let iconClass = 'tag-mutation';
      let name = 'Event';
      let desc = '';

      switch (parsed.type) {
        case 2:
          category = 'snapshot'; tagLabel = 'SNAP'; iconClass = 'tag-snapshot';
          name = $t.filterPages; desc = parsed.data?.href || ''; break;
        case 4:
          category = 'nav'; tagLabel = 'NAV'; iconClass = 'tag-nav';
          name = 'Navigation'; desc = parsed.data?.href || `${parsed.data?.width}×${parsed.data?.height}`; break;
        case 3:
          switch (parsed.data?.source) {
            case 0: category = 'mutation'; tagLabel = 'DOM'; iconClass = 'tag-mutation'; name = 'DOM Mutation'; break;
            case 1: category = 'other'; tagLabel = 'MOVE'; iconClass = 'tag-mutation'; name = 'Mouse Move'; break;
            case 2:
              category = 'click'; tagLabel = 'CLK'; iconClass = 'tag-click'; name = $t.filterClicks;
              if (parsed.data?.x !== undefined) desc = `${Math.round(parsed.data.x)}, ${Math.round(parsed.data.y)}`;
              break;
            case 3:
              category = 'scroll'; tagLabel = 'SCR'; iconClass = 'tag-scroll'; name = $t.filterScrolls;
              if (parsed.data?.y !== undefined) desc = `y: ${parsed.data.y}`; break;
            case 4: category = 'other'; tagLabel = 'RSZ'; iconClass = 'tag-mutation'; name = 'Resize'; break;
            case 5:
              category = 'input'; tagLabel = 'INP'; iconClass = 'tag-input'; name = $t.filterInputs;
              desc = parsed.data?.text ? `"${String(parsed.data.text).substring(0, 24)}"` : ''; break;
            default: name = 'Interaction'; break;
          }
          break;
        case 5: {
          const tag = String(parsed.data?.tag || 'CUSTOM');
          const payload = parsed.data?.payload || {};
          if (tag === 'ALERT' || tag === 'CONFIRM' || tag === 'PROMPT') {
            category = 'alert';
            tagLabel = tag === 'CONFIRM' ? 'CNF' : tag === 'PROMPT' ? 'PMT' : 'ALT';
            iconClass = 'tag-click';
            name = tag === 'CONFIRM' ? 'Confirm' : tag === 'PROMPT' ? 'Prompt' : 'Alert';
            desc = payload.message != null ? String(payload.message).substring(0, 48) : '';
            if (typeof payload.result === 'boolean') desc += ` → ${payload.result ? 'OK' : 'Cancel'}`;
            else if (payload.result != null && tag === 'PROMPT') desc += ` → "${String(payload.result).substring(0, 20)}"`;
          } else if (tag.startsWith('CONSOLE') || tag === 'ERROR' || tag === 'UNHANDLED_REJECTION') {
            category = 'log';
            tagLabel = 'LOG';
            iconClass = 'tag-input';
            name = tag.replace(/_/g, ' ');
            desc = payload.message
              ? String(payload.message).substring(0, 48)
              : (payload.args ? payload.args.join(' ').substring(0, 48) : payload.reason ? String(payload.reason).substring(0, 48) : '');
          } else if (tag.startsWith('FETCH')) {
            category = 'log';
            tagLabel = 'NET';
            iconClass = 'tag-nav';
            name = tag === 'FETCH_ERROR' ? 'Fetch Error' : 'Fetch';
            desc = payload.url ? `${payload.method || 'GET'} ${String(payload.url).substring(0, 40)}` : '';
          } else {
            category = 'log';
            tagLabel = 'EVT';
            iconClass = 'tag-mutation';
            name = tag;
            desc = payload.message ? String(payload.message).substring(0, 48) : '';
          }
          break;
        }
        default: name = `Type ${parsed.type}`;
      }

      return { idx, ts, category, tagLabel, iconClass, name, desc, parsed };
    });
  });

  let filteredEvents = $derived.by(() => {
    if (activityFilter === 'all') return parsedEvents;
    if (activityFilter === 'clicks') return parsedEvents.filter((e: any) => e.category === 'click');
    if (activityFilter === 'inputs') return parsedEvents.filter((e: any) => e.category === 'input');
    if (activityFilter === 'pages') return parsedEvents.filter((e: any) => e.category === 'nav' || e.category === 'snapshot');
    if (activityFilter === 'scrolls') return parsedEvents.filter((e: any) => e.category === 'scroll');
    if (activityFilter === 'alerts') return parsedEvents.filter((e: any) => e.category === 'alert' || e.category === 'log');
    return parsedEvents;
  });

  let visibleEvents = $derived(filteredEvents.slice(0, ACTIVITY_CAP));
  let isTruncated = $derived(filteredEvents.length > ACTIVITY_CAP);

  let firstTs = $derived(parsedEvents.length > 0 ? parsedEvents[0].ts : 0);

  function relativeTime(ts: number): string {
    if (!firstTs) return '0:00';
    const diff = Math.max(0, ts - firstTs);
    const s = Math.floor(diff / 1000);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  function handleSeek(item: any) {
    highlightedIndex = item.idx;
    if (onSeekTo) onSeekTo(item.ts - firstTs);
  }

  async function copySessionId() {
    if (!session?.sessionId) return;
    try {
      await navigator.clipboard.writeText(session.sessionId);
      copiedId = true;
      setTimeout(() => copiedId = false, 2000);
    } catch {}
  }

  let clickCount  = $derived(parsedEvents.filter((e: any) => e.category === 'click').length);
  let inputCount  = $derived(parsedEvents.filter((e: any) => e.category === 'input').length);
  let pageCount   = $derived(parsedEvents.filter((e: any) => e.category === 'nav' || e.category === 'snapshot').length);
  let scrollCount = $derived(parsedEvents.filter((e: any) => e.category === 'scroll').length);
  let alertCount  = $derived(parsedEvents.filter((e: any) => e.category === 'alert' || e.category === 'log').length);

  function sessionStatusLabel(s: any): string {
    if (!s) return '';
    if (s.status === 'STOPPED') return $t.statusEnded;
    const diffMin = (Date.now() - new Date(s.updatedAt).getTime()) / 60000;
    if (diffMin < 2)  return $t.statusLive;
    if (diffMin < 30) return $t.statusIdle;
    return $t.statusEnded;
  }

  let deviceInfo = $derived(session ? parseUserAgent(session.distinctId) : null);
  let metaTags = $derived(
    session
      ? buildSessionTags(session, { user: $t.tagUser, dept: $t.tagDept })
      : []
  );
  let sdkBadge = $derived(session ? formatSdkBadge(session) : null);

  function isEndedDetail(s: any): boolean {
    if (!s) return false;
    if (s.status === 'STOPPED') return true;
    const diffMin = (Date.now() - new Date(s.updatedAt).getTime()) / 60000;
    return diffMin >= 30;
  }

  function detailDuration(s: any): string {
    if (!s?.createdAt) return '—';
    const endIso = s.endAt || s.updatedAt;
    if (!endIso) return '—';
    return formatDurationMs(new Date(endIso).getTime() - new Date(s.createdAt).getTime());
  }

</script>

<!-- Tabs -->
<div class="tab-row">
  <button class="tab" class:active={activeTab === 'activity'} onclick={() => activeTab = 'activity'}>
    {$t.tabActivity} {events?.length ? `(${events.length})` : ''}
  </button>
  <button class="tab" class:active={activeTab === 'details'} onclick={() => activeTab = 'details'}>
    {$t.tabDetails}
  </button>
</div>

{#if activeTab === 'activity'}
  <!-- Filter chips -->
  <div class="activity-filters">
    <button class="filter-chip" class:active={activityFilter === 'all'}     onclick={() => activityFilter = 'all'}>
      {$t.filterAllEvents}
    </button>
    <button class="filter-chip" class:active={activityFilter === 'clicks'}  onclick={() => activityFilter = 'clicks'}>
      {$t.filterClicks} <span class="badge-count">{clickCount}</span>
    </button>
    <button class="filter-chip" class:active={activityFilter === 'inputs'}  onclick={() => activityFilter = 'inputs'}>
      {$t.filterInputs} <span class="badge-count">{inputCount}</span>
    </button>
    <button class="filter-chip" class:active={activityFilter === 'pages'}   onclick={() => activityFilter = 'pages'}>
      {$t.filterPages} <span class="badge-count">{pageCount}</span>
    </button>
    <button class="filter-chip" class:active={activityFilter === 'scrolls'} onclick={() => activityFilter = 'scrolls'}>
      {$t.filterScrolls} <span class="badge-count">{scrollCount}</span>
    </button>
    <button class="filter-chip" class:active={activityFilter === 'alerts'} onclick={() => activityFilter = 'alerts'}>
      Alerts <span class="badge-count">{alertCount}</span>
    </button>
  </div>

  <!-- Activity list -->
  <div class="activity-list">
    {#if loading}
      <div class="empty-state-clean activity-loading">
        <div class="spinner-clean"></div>
        <p>{$t.activityLoading}</p>
      </div>
    {:else if filteredEvents.length === 0}
      <div class="empty-state-clean">
        <p>{$t.noActivity}</p>
      </div>
    {:else}
      {#each visibleEvents as item (item.idx)}
        <button
          type="button"
          class="activity-item"
          class:highlighted={highlightedIndex === item.idx}
          onclick={() => handleSeek(item)}
          title="Seek to this event"
        >
          <span class="activity-tag {item.iconClass}">{item.tagLabel}</span>
          <div class="activity-body">
            <div class="activity-name">{item.name}</div>
            {#if item.desc}
              <div class="activity-desc">{item.desc}</div>
            {/if}
          </div>
          <div class="activity-time">{relativeTime(item.ts)}</div>
        </button>
      {/each}
      {#if isTruncated}
        <div class="activity-truncated-note">{$t.activityTruncated}</div>
      {/if}
    {/if}
  </div>

{:else}
  <!-- Details tab -->
  <div class="details-panel">
    {#if session}
      {#if deviceInfo}
        <div class="device-hero">
          <div class="device-hero-title">{$t.detailDeviceEnv}</div>
          <div class="device-cards">
            <div class="device-card browser-{deviceInfo.browserKey}">
              <div class="device-card-icon">
                <DeviceIcons kind={deviceInfo.browserKey} size={22} />
              </div>
              <div class="device-card-body">
                <div class="device-card-label">{$t.detailBrowser}</div>
                <div class="device-card-name">{deviceInfo.browser}</div>
                <div class="device-card-ver">{deviceInfo.browserVersion || '—'}</div>
              </div>
            </div>
            <div class="device-card os-{deviceInfo.osKey}">
              <div class="device-card-icon">
                <DeviceIcons kind={deviceInfo.osKey} size={22} />
              </div>
              <div class="device-card-body">
                <div class="device-card-label">{$t.detailOs}</div>
                <div class="device-card-name">{deviceInfo.os}</div>
                <div class="device-card-ver">{deviceInfo.osVersion || '—'}</div>
              </div>
            </div>
            <div class="device-card device-{deviceInfo.deviceKey}">
              <div class="device-card-icon">
                <DeviceIcons kind={deviceInfo.deviceKey} size={22} />
              </div>
              <div class="device-card-body">
                <div class="device-card-label">{$t.detailDevice}</div>
                <div class="device-card-name">{deviceInfo.device}</div>
                <div class="device-card-ver">{deviceInfo.deviceKey}</div>
              </div>
            </div>
          </div>
        </div>
      {/if}

      {#if metaTags.length}
        <div class="detail-section-label">{$t.detailTags}</div>
        <div class="sc-tags detail-tags">
          {#each metaTags as tag}
            <span class="meta-tag kind-{tag.kind}">
              <DeviceIcons
                kind={tag.kind === 'browser' ? (deviceInfo?.browserKey ?? 'chrome')
                  : tag.kind === 'os' ? (deviceInfo?.osKey ?? 'windows')
                  : tag.kind === 'device' ? (deviceInfo?.deviceKey ?? 'desktop')
                  : tag.kind === 'user' ? 'user'
                  : tag.kind === 'dept' ? 'dept'
                  : 'other'}
                size={11}
              />
              {#if tag.kind === 'user' || tag.kind === 'dept' || tag.kind === 'custom'}
                <span class="meta-tag-label">{tag.label}</span>
              {/if}
              <span class="meta-tag-value">{tag.value}</span>
            </span>
          {/each}
        </div>
      {/if}

      {#if sdkBadge || session.sdkName || session.sdkVersion || session.sdkIntegration}
        <div class="detail-section-label">{$t.detailSdk}</div>
        {#if sdkBadge}
          <div class="detail-row">
            <span class="detail-key">{$t.detailSdkBadge}</span>
            <span class="detail-val"><span class="meta-tag kind-sdk">{sdkBadge}</span></span>
          </div>
        {/if}
        {#if session.sdkName}
          <div class="detail-row">
            <span class="detail-key">{$t.detailSdkName}</span>
            <span class="detail-val">{session.sdkName}</span>
          </div>
        {/if}
        {#if session.sdkVersion}
          <div class="detail-row">
            <span class="detail-key">{$t.detailSdkVersion}</span>
            <span class="detail-val">{session.sdkVersion}</span>
          </div>
        {/if}
        {#if session.sdkIntegration}
          <div class="detail-row">
            <span class="detail-key">{$t.detailSdkIntegration}</span>
            <span class="detail-val">{session.sdkIntegration}</span>
          </div>
        {/if}
      {/if}

      <div class="detail-section-label">{$t.detailMeta}</div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailSystem}</span>
        <span class="detail-val">{session.projectName ?? $t.unknown}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailStatus}</span>
        <span class="detail-val">{sessionStatusLabel(session)}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailUserId}</span>
        <span class="detail-val">{session.userId || $t.anonymous}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailEvents}</span>
        <span class="detail-val">{events?.length || 0}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailClicks}</span>
        <span class="detail-val">{clickCount}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailInputs}</span>
        <span class="detail-val">{inputCount}</span>
      </div>
      <div class="detail-row">
        <span class="detail-key">{$t.detailCreated}</span>
        <span class="detail-val mono-time">{formatDateTime(session.createdAt)}</span>
      </div>
      {#if isEndedDetail(session)}
        <div class="detail-row">
          <span class="detail-key">{$t.detailEnded}</span>
          <span class="detail-val mono-time">{formatDateTime(session.endAt || session.updatedAt)}</span>
        </div>
        <div class="detail-row">
          <span class="detail-key">{$t.detailDuration}</span>
          <span class="detail-val mono-time">
            {formatRange(session.createdAt, session.endAt || session.updatedAt)}
            <span class="dur-chip">{detailDuration(session)}</span>
          </span>
        </div>
      {:else}
        <div class="detail-row">
          <span class="detail-key">{$t.detailLastActive}</span>
          <span class="detail-val mono-time">{formatDateTime(session.updatedAt)}</span>
        </div>
      {/if}
      {#if session.distinctId}
        <div class="detail-row detail-row-id">
          <span class="detail-key">{$t.detailDistinctId}</span>
          <span class="detail-val mono ua-raw" title={session.distinctId}>{session.distinctId}</span>
        </div>
      {/if}
      <div class="detail-row detail-row-id">
        <span class="detail-key">{$t.detailSessionId}</span>
        <div class="session-id-row">
          <span class="detail-val mono session-id-full" title={session.sessionId}>
            {session.sessionId}
          </span>
          <button
            type="button"
            class="btn-copy-id"
            onclick={copySessionId}
            title={$t.copySessionId}
          >
            {copiedId ? $t.copied : $t.copySessionId}
          </button>
        </div>
      </div>
    {:else}
      <div class="empty-state-clean empty-state-spaced">
        <p>{$t.noSessionMeta}</p>
      </div>
    {/if}
  </div>
{/if}


