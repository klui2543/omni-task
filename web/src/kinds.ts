import type { KindsState } from './types'

// The task kinds the owner manages (their own kinds, and the built-in ones hidden). Android keeps these in its synced
// settings; the web keeps them on this device only (localStorage `omni.kinds`) until the owner decides how settings sync.
// The state is in Android's form, so custom kinds are "name\temoji\ttag" and hidden ones are TaskKind names.

const KEY = 'omni.kinds'

const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [])

export const kindsStore = {
  get(): KindsState {
    try {
      const raw = JSON.parse(localStorage.getItem(KEY) ?? '{}')
      return { custom: strings(raw?.custom), hidden: strings(raw?.hidden) }
    } catch {
      return { custom: [], hidden: [] }
    }
  },
  set(state: KindsState) {
    try { localStorage.setItem(KEY, JSON.stringify(state)) } catch { /* just not kept */ }
  },
}

/** The tag of every kind the owner made, from the state as stored. */
export const customKindTags = (state: KindsState = kindsStore.get()): string[] =>
  state.custom.map((e) => e.split('\t')).filter((p) => p.length === 3 && p[2].trim()).map((p) => p[2])

/** The first character the owner typed as a kind's emoji (a whole emoji, not half of one). */
export const firstGlyph = (text: string): string => {
  const t = text.trim()
  if (!t) return ''
  const Segmenter = (Intl as unknown as { Segmenter?: new (l?: string, o?: object) => { segment(s: string): Iterable<{ segment: string }> } }).Segmenter
  if (Segmenter) for (const s of new Segmenter(undefined, { granularity: 'grapheme' }).segment(t)) return s.segment
  return Array.from(t)[0]
}
