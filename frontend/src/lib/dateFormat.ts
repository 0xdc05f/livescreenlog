/** Zero-pad 2 digits */
function p2(n: number): string {
  return String(n).padStart(2, '0');
}

function toDate(iso: string | Date | null | undefined): Date | null {
  if (iso == null || iso === '') return null;
  const d = iso instanceof Date ? iso : new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Always full: 2026-07-22 15:24 */
export function formatDateTime(iso: string | Date | null | undefined, opts?: { seconds?: boolean }): string {
  const d = toDate(iso);
  if (!d) return '—';
  const y = d.getFullYear();
  const m = p2(d.getMonth() + 1);
  const day = p2(d.getDate());
  const h = p2(d.getHours());
  const min = p2(d.getMinutes());
  if (opts?.seconds) {
    return `${y}-${m}-${day} ${h}:${min}:${p2(d.getSeconds())}`;
  }
  return `${y}-${m}-${day} ${h}:${min}`;
}

/** Compact alias — still always includes Y-M-D + 24h time */
export function formatCompact(iso: string | Date | null | undefined, _ref?: Date): string {
  return formatDateTime(iso);
}

/**
 * Range always with full dates (24h).
 * Same calendar day: 2026-07-22 14:10 – 15:42
 * Cross day:         2026-07-21 23:10 – 2026-07-22 01:05
 */
export function formatRange(
  startIso: string | Date | null | undefined,
  endIso: string | Date | null | undefined,
  _ref?: Date
): string {
  const s = toDate(startIso);
  const e = toDate(endIso);
  if (!s && !e) return '—';
  if (!s) return formatDateTime(e);
  if (!e) return formatDateTime(s);

  const sameDay =
    s.getFullYear() === e.getFullYear() &&
    s.getMonth() === e.getMonth() &&
    s.getDate() === e.getDate();

  const startPart = formatDateTime(s);
  if (sameDay) {
    const endTime = `${p2(e.getHours())}:${p2(e.getMinutes())}`;
    return `${startPart} – ${endTime}`;
  }
  return `${formatDateTime(s)} – ${formatDateTime(e)}`;
}

export function formatDurationMs(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${p2(m)}m`;
  if (m > 0) return `${m}m ${p2(s)}s`;
  return `${s}s`;
}
