import { useState } from 'preact/hooks'
import { today } from './core'
import type { Kind } from './types'

// What the owner chose on the Focus page, kept on this device (Android keeps the same choices in its settings).

const KEY = 'omni.focus'

export interface FocusLocal {
  /** "date|title" of future work skipped for that day, so tomorrow's pick moves on. */
  skipped: string[]
  /** Ids of suggestions waved away. */
  dismissed: string[]
  /** Review date by task title. */
  reviewed: Record<string, string>
  futureCount: number
  countdown: string | null
  /** Tonight's bedtime when it is not the usual one. */
  tonightBed: { evening: string; time: string } | null
}

const EMPTY: FocusLocal = { skipped: [], dismissed: [], reviewed: {}, futureCount: 1, countdown: null, tonightBed: null }

const load = (): FocusLocal => {
  try {
    return { ...EMPTY, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
  } catch {
    return EMPTY
  }
}

export function useFocusLocal(): [FocusLocal, (change: (s: FocusLocal) => FocusLocal) => void] {
  const [state, setState] = useState<FocusLocal>(load)
  return [
    state,
    (change) => {
      const next = change(state)
      setState(next)
      try { localStorage.setItem(KEY, JSON.stringify(next)) } catch { /* just not kept */ }
    },
  ]
}

/** The titles skipped for today only. */
export const skippedToday = (s: FocusLocal) => s.skipped.filter((e) => e.startsWith(today() + '|')).map((e) => e.slice(11))

export const KIND_LABEL: Record<Kind, string> = { NORMAL: 'ปกติ', WAITING: 'มีคนรอ', FUTURE: 'ลงทุนอนาคต', SOMEDAY: 'พักไว้ก่อน' }
