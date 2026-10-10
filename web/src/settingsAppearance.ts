// Theme, palette, font and text size, kept on this device (Android's Appearance, which it also syncs through its
// settings file; the web does not read that file). Importing this file applies the saved choice at once, so the
// app never flashes the default look.

export type ThemeMode = 'system' | 'light' | 'dark'
export type PaletteChoice = 'linear' | 'midnight'
export type FontChoice = 'sarabun' | 'plex' | 'noto' | 'prompt' | 'system'

export const THEMES: [ThemeMode, string][] = [['system', 'ตามระบบ'], ['light', 'สว่าง'], ['dark', 'มืด']]
export const PALETTES: [PaletteChoice, string][] = [['linear', 'Linear'], ['midnight', 'สีเดิม (มืดเท่านั้น)']]
export const SCALES = [0.9, 1, 1.15, 1.3]

export const FONTS: { id: FontChoice; label: string; family: string; google: string | null }[] = [
  { id: 'sarabun', label: 'Sarabun (คล้าย TH Sarabun)', family: 'Sarabun', google: null },
  { id: 'plex', label: 'IBM Plex Sans Thai Looped', family: '"IBM Plex Sans Thai Looped"', google: 'IBM+Plex+Sans+Thai+Looped:wght@400;500' },
  { id: 'noto', label: 'Noto Sans Thai Looped', family: '"Noto Sans Thai Looped"', google: 'Noto+Sans+Thai+Looped:wght@400;500' },
  { id: 'prompt', label: 'Prompt (ไม่มีหัว)', family: 'Prompt', google: 'Prompt:wght@400;500' },
  { id: 'system', label: 'ฟอนต์ของระบบ', family: 'system-ui', google: null },
]

export interface Appearance {
  theme: ThemeMode
  palette: PaletteChoice
  font: FontChoice
  scale: number
}

const KEY = 'omni.appearance'
const DEFAULT: Appearance = { theme: 'system', palette: 'linear', font: 'sarabun', scale: 1 }

export function appearance(): Appearance {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}')
    return {
      theme: THEMES.some(([k]) => k === saved.theme) ? saved.theme : DEFAULT.theme,
      palette: PALETTES.some(([k]) => k === saved.palette) ? saved.palette : DEFAULT.palette,
      font: FONTS.some((f) => f.id === saved.font) ? saved.font : DEFAULT.font,
      scale: SCALES.includes(saved.scale) ? saved.scale : DEFAULT.scale,
    }
  } catch {
    return DEFAULT
  }
}

const systemDark = () => {
  try { return matchMedia('(prefers-color-scheme: dark)').matches } catch { return false }
}

const loaded = new Set<string>()

/** Puts [a] on the page: the colours by attributes on the root (see style.css), the font and size by variables. */
export function apply(a: Appearance = appearance()) {
  const root = document.documentElement
  // The first look is dark only, whatever the theme says.
  const dark = a.palette === 'midnight' || a.theme === 'dark' || (a.theme === 'system' && systemDark())
  root.dataset.theme = dark ? 'dark' : 'light'
  root.dataset.palette = a.palette
  const font = FONTS.find((f) => f.id === a.font)!
  root.style.setProperty('--app-font', `${font.family}, Sarabun, system-ui, "Noto Sans Thai", sans-serif`)
  root.style.setProperty('--text-scale', String(a.scale))
  if (font.google && !loaded.has(font.id)) {
    loaded.add(font.id)
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = `https://fonts.googleapis.com/css2?family=${font.google}&display=swap`
    document.head.appendChild(link)
  }
}

export function setAppearance(change: Partial<Appearance>) {
  const next = { ...appearance(), ...change }
  try { localStorage.setItem(KEY, JSON.stringify(next)) } catch { /* just not kept */ }
  apply(next)
}

apply()
try {
  // "Follow the system" follows the system while the page is open, too.
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => apply())
} catch { /* an old browser: the choice is read on the next open */ }
