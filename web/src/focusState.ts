import { useEffect, useState } from 'preact/hooks'
import { today } from './core'
import { settingsBus } from './settingsBus'
import type { Kind } from './types'

// What the owner chose on the Focus page, kept on this device and shared with Android through its settings file
// (settingsSync.ts), where Android keeps the same choices.

const KEY = 'omni.focus'

export interface FocusLocal {
  /** "date|title" of future work skipped for that day, so tomorrow's pick moves on. */
  skipped: string[]
  /** Ids of suggestions waved away. */
  dismissed: string[]
  /** Review date by task title. */
  reviewed: Record<string, string>
  futureCount: number
  /** The single countdown (a task title) kept before there could be several; read until [countdowns] is saved. */
  countdown: string | null
  /** The pinned countdowns, as their pin keys. */
  countdowns?: string[]
  /** Tonight's bedtime when it is not the usual one. */
  tonightBed: { evening: string; time: string } | null
  /** Tonight's wake time when it is not the usual one (a morning event no longer moves it). */
  tonightWake?: { evening: string; time: string } | null
  /** Tonight's wake time was set on the watch (it comes from Android; a time picked here is not). */
  wakeFromWatch?: boolean
}

const EMPTY: FocusLocal = { skipped: [], dismissed: [], reviewed: {}, futureCount: 1, countdown: null, tonightBed: null }

/** The choices as kept on this device; settingsSync.ts reads and writes them too. */
export const focusLocal = {
  get(): FocusLocal {
    try {
      return { ...EMPTY, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
    } catch {
      return EMPTY
    }
  },
  set(s: FocusLocal) {
    try { localStorage.setItem(KEY, JSON.stringify(s)) } catch { /* just not kept */ }
  },
}

/** Pages showing the choices, told when one is made elsewhere (the edit panel marks a task reviewed). */
const watchers = new Set<() => void>()

/** Marks a task reviewed today, as Android does when its kind is changed; the Focus page, if open, sees it at once. */
export function markReviewed(title: string) {
  const s = focusLocal.get()
  focusLocal.set({ ...s, reviewed: { ...s.reviewed, [title]: today() } })
  watchers.forEach((w) => w())
  settingsBus.changed()
}

export function useFocusLocal(): [FocusLocal, (change: (s: FocusLocal) => FocusLocal) => void] {
  const [state, setState] = useState<FocusLocal>(focusLocal.get)
  useEffect(() => {
    const again = () => setState(focusLocal.get())
    watchers.add(again)
    return () => { watchers.delete(again) }
  }, [])
  return [
    state,
    (change) => {
      const next = change(state)
      setState(next)
      focusLocal.set(next)
      settingsBus.changed()
    },
  ]
}

/** The pinned countdowns, the single one of before included. */
export const countdownPins = (s: FocusLocal) => s.countdowns ?? (s.countdown ? [s.countdown] : [])

/** The titles skipped for today only. */
export const skippedToday = (s: FocusLocal) => s.skipped.filter((e) => e.startsWith(today() + '|')).map((e) => e.slice(11))

export const KIND_LABEL: Record<Kind, string> = { NORMAL: 'ปกติ', WAITING: 'มีคนรอ', FUTURE: 'ลงทุนอนาคต', SOMEDAY: 'พักไว้ก่อน' }
