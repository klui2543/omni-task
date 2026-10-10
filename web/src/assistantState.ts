import { useEffect, useState } from 'preact/hooks'
import type { AgendaOut, PlanOut, Proposal, Ranked } from './assistantCore'

// What the Assistant keeps on this device (Android keeps the same in its preferences): the log of when work got done,
// the questions answered "no", and the conversation of the session.

const DONE_KEY = 'omni.doneLog'
const DECLINED_KEY = 'omni.declinedInsights'

const read = <T>(key: string, fallback: T): T => {
  try {
    return JSON.parse(localStorage.getItem(key) ?? 'null') ?? fallback
  } catch {
    return fallback
  }
}
const write = (key: string, value: unknown) => {
  try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* just not kept */ }
}

const pad = (n: number) => String(n).padStart(2, '0')
/** A moment in the device's own time zone as `yyyy-MM-ddTHH:mm:ss`. */
const stamp = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

export const doneLog = {
  get: (): string[] => read<string[]>(DONE_KEY, []),
  /** Remembers when work gets done and what kind it was, so the assistant can notice the owner's rhythm (the last 200). */
  add: (kind: string, at = new Date()) => write(DONE_KEY, [...doneLog.get(), `${stamp(at)}|${kind}`].sort().slice(-200)),
}

export const declined = {
  get: (): string[] => read<string[]>(DECLINED_KEY, []),
  add: (id: string) => write(DECLINED_KEY, [...new Set([...declined.get(), id])]),
}

/** One entry of the conversation. */
export type Chat =
  | { t: 'asked'; text: string }
  | { t: 'duration'; request: string; answered?: number }
  | { t: 'slots'; plan: PlanOut; page: number; picked: number; title: string; done?: string; toCalendar?: boolean }
  | { t: 'today'; items: Ranked[] }
  | { t: 'ranked'; items: Ranked[] }
  | { t: 'review'; title: string; lines: string[] }
  | { t: 'agenda'; title: string; summary: string; data: AgendaOut }
  | { t: 'range'; title: string; proposals: Proposal[]; accepted: number[]; toCalendar?: boolean }

const RESUME_KEY = 'omni.chatResume'

/** Keeps the conversation through a trip to Google (asking for the calendar permission leaves the page). */
export function keepChatForReturn() {
  try { sessionStorage.setItem(RESUME_KEY, JSON.stringify(chat)) } catch { /* the conversation just starts afresh */ }
}

const takeKeptChat = (): Chat[] => {
  try {
    const kept = sessionStorage.getItem(RESUME_KEY)
    sessionStorage.removeItem(RESUME_KEY)
    return kept ? JSON.parse(kept) : []
  } catch {
    return []
  }
}

// The conversation lives as long as the page is open, as Android's does for the session: leaving for another page
// and coming back finds it again, a reload starts afresh (but not a trip to Google for the calendar permission).
let chat: Chat[] = takeKeptChat()
const listeners = new Set<() => void>()

export function useChat(): [Chat[], (change: (c: Chat[]) => Chat[]) => void] {
  const [, force] = useState(0)
  useEffect(() => {
    const l = () => force((n) => n + 1)
    listeners.add(l)
    return () => { listeners.delete(l) }
  }, [])
  return [chat, (change) => { chat = change(chat); listeners.forEach((l) => l()) }]
}
