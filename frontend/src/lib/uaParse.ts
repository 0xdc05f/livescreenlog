export type DeviceInfo = {
  browser: string;
  browserVersion: string;
  browserKey: 'chrome' | 'edge' | 'firefox' | 'safari' | 'opera' | 'samsung' | 'ie' | 'other';
  os: string;
  osVersion: string;
  osKey: 'windows' | 'macos' | 'ios' | 'android' | 'linux' | 'chromeos' | 'other';
  device: string;
  deviceKey: 'desktop' | 'mobile' | 'tablet' | 'other';
  raw: string;
};

function match(re: RegExp, s: string): RegExpMatchArray | null {
  return s.match(re);
}

export function parseUserAgent(ua: string | null | undefined): DeviceInfo | null {
  if (!ua || !ua.trim()) return null;
  const s = ua.trim();

  let browser = 'Other';
  let browserVersion = '';
  let browserKey: DeviceInfo['browserKey'] = 'other';

  if (/Edg\//i.test(s)) {
    browser = 'Edge';
    browserKey = 'edge';
    browserVersion = match(/Edg\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/OPR\/|Opera/i.test(s)) {
    browser = 'Opera';
    browserKey = 'opera';
    browserVersion = match(/(?:OPR|Opera)\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/SamsungBrowser\//i.test(s)) {
    browser = 'Samsung';
    browserKey = 'samsung';
    browserVersion = match(/SamsungBrowser\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/Chrome\//i.test(s) && !/Chromium/i.test(s)) {
    browser = 'Chrome';
    browserKey = 'chrome';
    browserVersion = match(/Chrome\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/Firefox\//i.test(s)) {
    browser = 'Firefox';
    browserKey = 'firefox';
    browserVersion = match(/Firefox\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/Safari\//i.test(s) && !/Chrome|Chromium|Edg/i.test(s)) {
    browser = 'Safari';
    browserKey = 'safari';
    browserVersion = match(/Version\/([\d.]+)/i, s)?.[1] ?? match(/Safari\/([\d.]+)/i, s)?.[1] ?? '';
  } else if (/MSIE |Trident\//i.test(s)) {
    browser = 'IE';
    browserKey = 'ie';
    browserVersion = match(/(?:MSIE |rv:)([\d.]+)/i, s)?.[1] ?? '';
  }

  let os = 'Other';
  let osVersion = '';
  let osKey: DeviceInfo['osKey'] = 'other';

  if (/Windows NT/i.test(s)) {
    os = 'Windows';
    osKey = 'windows';
    const nt = match(/Windows NT ([\d.]+)/i, s)?.[1] ?? '';
    const map: Record<string, string> = {
      '10.0': '10/11',
      '6.3': '8.1',
      '6.2': '8',
      '6.1': '7',
      '6.0': 'Vista',
      '5.1': 'XP',
    };
    osVersion = map[nt] ?? nt;
  } else if (/Mac OS X/i.test(s)) {
    os = 'macOS';
    osKey = 'macos';
    osVersion = (match(/Mac OS X ([\d_]+)/i, s)?.[1] ?? '').replace(/_/g, '.');
  } else if (/iPhone|iPad|iPod/i.test(s)) {
    os = 'iOS';
    osKey = 'ios';
    osVersion = (match(/OS ([\d_]+)/i, s)?.[1] ?? '').replace(/_/g, '.');
  } else if (/Android/i.test(s)) {
    os = 'Android';
    osKey = 'android';
    osVersion = match(/Android ([\d.]+)/i, s)?.[1] ?? '';
  } else if (/CrOS/i.test(s)) {
    os = 'ChromeOS';
    osKey = 'chromeos';
  } else if (/Linux/i.test(s)) {
    os = 'Linux';
    osKey = 'linux';
  }

  let device = 'Desktop';
  let deviceKey: DeviceInfo['deviceKey'] = 'desktop';
  if (/iPad|Tablet|PlayBook/i.test(s) || (/Android/i.test(s) && !/Mobile/i.test(s))) {
    device = 'Tablet';
    deviceKey = 'tablet';
  } else if (/Mobi|iPhone|iPod|Android.*Mobile|webOS|BlackBerry/i.test(s)) {
    device = 'Mobile';
    deviceKey = 'mobile';
  }

  // Short brand hint from common Android model tokens
  const model = match(/;\s*([^;)]+)\s+Build\//i, s)?.[1]?.trim();
  if (model && deviceKey !== 'desktop') {
    device = model.length > 28 ? model.slice(0, 26) + '…' : model;
  }

  return {
    browser,
    browserVersion: browserVersion.split('.').slice(0, 2).join('.'),
    browserKey,
    os,
    osVersion,
    osKey,
    device,
    deviceKey,
    raw: s,
  };
}

export type MetaTag = {
  key: string;
  label: string;
  value: string;
  kind: 'user' | 'dept' | 'browser' | 'os' | 'device' | 'source' | 'other' | 'custom';
};

/** Build display tags from session fields (userId, distinctId UA, custom tags). Path/source is omitted — it changes during navigation. */
export function buildSessionTags(session: {
  userId?: string | null;
  distinctId?: string | null;
  source?: string | null;
  tags?: Record<string, string> | null;
}, labels: { user: string; dept: string; source?: string }): MetaTag[] {
  const tags: MetaTag[] = [];
  const uid = (session.userId || '').trim();
  if (uid) {
    // Support "사번|부서" or "사번 / 부서" composite userId
    const parts = uid.split(/\s*[|/]\s*/).map((p) => p.trim()).filter(Boolean);
    if (parts.length >= 2) {
      tags.push({ key: 'user', label: labels.user, value: parts[0], kind: 'user' });
      tags.push({ key: 'dept', label: labels.dept, value: parts.slice(1).join(' / '), kind: 'dept' });
    } else {
      tags.push({ key: 'user', label: labels.user, value: uid, kind: 'user' });
    }
  }

  // Custom tags from SDK (dept, role, app, …) — skip keys already covered
  const custom = session.tags;
  if (custom && typeof custom === 'object') {
    const skip = new Set(['user', 'userid', 'user_id']);
    for (const [key, value] of Object.entries(custom)) {
      if (!key || value == null || value === '') continue;
      const lk = key.toLowerCase();
      if (skip.has(lk)) continue;
      // Prefer dedicated dept tag styling when key is dept
      if (lk === 'dept' || lk === 'department') {
        // Avoid duplicate if already parsed from userId composite
        if (!tags.some((t) => t.kind === 'dept')) {
          tags.push({ key, label: labels.dept, value: String(value), kind: 'dept' });
        }
        continue;
      }
      tags.push({ key, label: key, value: String(value), kind: 'custom' });
    }
  }

  const info = parseUserAgent(session.distinctId);
  if (info) {
    const b = info.browserVersion ? `${info.browser} ${info.browserVersion}` : info.browser;
    tags.push({ key: 'browser', label: b, value: b, kind: 'browser' });
    const o = info.osVersion ? `${info.os} ${info.osVersion}` : info.os;
    tags.push({ key: 'os', label: o, value: o, kind: 'os' });
    tags.push({ key: 'device', label: info.device, value: info.device, kind: 'device' });
  } else if (session.distinctId) {
    const short =
      session.distinctId.length > 36
        ? session.distinctId.slice(0, 34) + '…'
        : session.distinctId;
    tags.push({ key: 'device', label: short, value: short, kind: 'other' });
  }

  return tags;
}

/** Format SDK badge text e.g. browser@1.1.0 or vue@1.1.0 */
export function formatSdkBadge(session: {
  sdkName?: string | null;
  sdkVersion?: string | null;
  sdkIntegration?: string | null;
}): string | null {
  const integration = (session.sdkIntegration || '').trim();
  const version = (session.sdkVersion || '').trim();
  const name = (session.sdkName || '').trim();
  if (integration && version) return `${integration}@${version}`;
  if (name && version) {
    const short = name.replace(/^livescreenlog-/, '');
    return `${short}@${version}`;
  }
  if (integration) return integration;
  if (name) return name;
  return null;
}
