import { writable, derived, get } from 'svelte/store';
import ko from './ko';
import en from './en';

export type Locale = 'ko' | 'en';
export type Translations = typeof en;

// Cast keeps Record assignment stable if function signatures differ slightly;
// keys must still match en (verified at build time via usage sites).
const translations = {
  en,
  ko: ko as unknown as Translations,
} satisfies Record<Locale, Translations>;

/** Detect browser language, fallback to 'en' */
function detectLocale(): Locale {
  const saved = localStorage.getItem('sl_locale') as Locale | null;
  if (saved && (saved === 'ko' || saved === 'en')) return saved;

  const lang = navigator.language || 'en';
  return lang.startsWith('ko') ? 'ko' : 'en';
}

/** Reactive locale store */
export const locale = writable<Locale>(detectLocale());

/** Persist locale changes */
locale.subscribe((val) => {
  try { localStorage.setItem('sl_locale', val); } catch {}
});

/** Reactive translation object */
export const t = derived(locale, ($locale) => translations[$locale]);

/** Toggle between ko and en */
export function toggleLocale() {
  locale.update((l) => (l === 'ko' ? 'en' : 'ko'));
}

/** Convenience: get current translations synchronously */
export function getT(): Translations {
  return translations[get(locale)];
}

export { ko, en };
