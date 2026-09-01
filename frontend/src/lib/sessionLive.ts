export const LIVE_WINDOW_MS = 5 * 60 * 1000;
export function isSessionLive(session: { status?: string; updatedAt?: string } | null | undefined): boolean {
  if (!session || session.status === 'STOPPED') return false;
  if (!session.updatedAt) return session.status === 'ACTIVE';
  const t = new Date(session.updatedAt).getTime();
  if (!isFinite(t)) return session.status === 'ACTIVE';
  return Date.now() - t < LIVE_WINDOW_MS;
}
