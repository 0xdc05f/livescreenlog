export const LIVE_WINDOW_MS = 5 * 60 * 1000;
export const IDLE_WINDOW_MS = 30 * 60 * 1000;

export function isSessionLive(session: { status?: string; updatedAt?: string } | null | undefined): boolean {
  return sessionActivity(session) === 'live';
}

export function sessionActivity(
  session: { status?: string; updatedAt?: string } | null | undefined
): 'live' | 'idle' | 'ended' {
  if (!session || session.status === 'STOPPED') return 'ended';
  if (!session.updatedAt) return session.status === 'ACTIVE' ? 'live' : 'ended';
  const t = new Date(session.updatedAt).getTime();
  if (!isFinite(t)) return session.status === 'ACTIVE' ? 'live' : 'ended';
  const age = Date.now() - t;
  if (age < LIVE_WINDOW_MS) return 'live';
  if (age < IDLE_WINDOW_MS) return 'idle';
  return 'ended';
}
