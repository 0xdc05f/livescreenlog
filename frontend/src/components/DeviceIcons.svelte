<script lang="ts">
  let { kind, size = 14 }: { kind: string; size?: number } = $props();
</script>

<svg
  class="dev-icon"
  width={size}
  height={size}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.75"
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"
>
  {#if kind === 'chrome'}
    <circle cx="12" cy="12" r="10" />
    <circle cx="12" cy="12" r="4" />
    <path d="M21.17 8H12" />
    <path d="M3.95 6.06 8.54 14" />
    <path d="M10.88 21.94 15.46 14" />
  {:else if kind === 'edge'}
    <circle cx="12" cy="12" r="10" />
    <path d="M12 16a4 4 0 1 0 0-8 4 4 0 0 0-3.5 6" />
    <path d="M18 12c0 4-3 7-7 8" />
  {:else if kind === 'firefox'}
    <circle cx="12" cy="12" r="10" />
    <path d="M8 9c1-3 4-4 6-3 2 3 1 7-1 9-3 1-6-1-7-4 2 0 3-1 2-2Z" />
  {:else if kind === 'safari'}
    <circle cx="12" cy="12" r="10" />
    <path d="m16 8-2.5 6.5L7 17l2.5-6.5L16 8Z" />
    <circle cx="12" cy="12" r="1" fill="currentColor" />
  {:else if kind === 'opera'}
    <circle cx="12" cy="12" r="10" />
    <ellipse cx="12" cy="12" rx="4" ry="7" />
  {:else if kind === 'windows'}
    <path d="M3 5.5 10 4.5v6.2H3V5.5Zm8-.9L21 3v7.7h-10V4.6ZM3 12.8h7V19L3 18v-5.2Zm8 0h10V21l-10-1.4v-6.8Z" fill="currentColor" stroke="none" />
  {:else if kind === 'macos' || kind === 'ios'}
    <path d="M12 3c.8 0 1.9.5 2.5 1.2.5.6.9 1.5.8 2.4-1 .1-2-.4-2.6-1C12.1 4.9 11.7 4 12 3Z" fill="currentColor" stroke="none" />
    <path d="M16.8 8.2c-1.1-.7-2.5-.6-3.3-.1-.9.5-1.6.5-2.5 0-1.1-.6-2.4-.7-3.5 0C5.2 9.6 4 12.2 4.4 15.4c.3 2.2 1.3 4.3 2.6 4.6.8.2 1.3-.3 2.1-.3.8 0 1.2.3 2.1.3 1.1 0 1.9-1.5 2.6-3.1.5-1.1.7-2.2.7-2.3 0 0-2.3-.9-2.3-3.4 0-2.1 1.7-3.1 1.8-3.1.5 0 1.5.4 2.8 1.1Z" fill="currentColor" stroke="none" />
  {:else if kind === 'android'}
    <path d="M8 10v6M16 10v6M7 16h10v2a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2v-2Z" />
    <path d="M8 10a4 4 0 0 1 8 0" />
    <path d="m9 6-.5-1.5M15 6l.5-1.5" />
  {:else if kind === 'linux'}
    <circle cx="12" cy="8" r="3.5" />
    <path d="M8.5 11c-1 2-.5 6 1.5 7.5h4c2-1.5 2.5-5.5 1.5-7.5" />
    <path d="M9 21h6" />
  {:else if kind === 'mobile'}
    <rect x="7" y="2" width="10" height="20" rx="2" />
    <path d="M11 18h2" />
  {:else if kind === 'tablet'}
    <rect x="4" y="3" width="16" height="18" rx="2" />
    <path d="M11 17h2" />
  {:else if kind === 'desktop'}
    <rect x="2" y="4" width="20" height="13" rx="2" />
    <path d="M8 21h8M12 17v4" />
  {:else if kind === 'user'}
    <circle cx="12" cy="8" r="4" />
    <path d="M4 20c1.5-3.5 4-5 8-5s6.5 1.5 8 5" />
  {:else if kind === 'dept'}
    <path d="M3 21h18M5 21V7l7-4 7 4v14" />
    <path d="M9 21v-6h6v6M9 10h.01M15 10h.01M12 10h.01M12 14h.01" />
  {:else if kind === 'source'}
    <path d="M4 19h16M7 16l5-10 5 10" />
  {:else}
    <circle cx="12" cy="12" r="9" />
    <path d="M12 8v4M12 16h.01" />
  {/if}
</svg>

<style>
  .dev-icon {
    flex-shrink: 0;
    display: block;
  }
</style>
