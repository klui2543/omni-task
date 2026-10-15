import { useLayoutEffect, useRef, useState } from 'preact/hooks'
import type { AgendaOut, PlanOut, Proposal, Ranked } from './assistantCore'
import type { GeminiChange } from './gemini'

// The conversation of the Assistant's session. When work got done and the questions answered "no" are in the
// behavior log the devices share (behavior.ts).

/** One entry of the conversation. */
export type Chat =
  | { t: 'asked'; text: string }
  | { t: 'duration'; request: string; answered?: number }
  | { t: 'slots'; plan: PlanOut; page: number; picked: number; title: string; done?: string; toCalendar?: boolean }
  | { t: 'today'; items: Ranked[] }
  | { t: 'ranked'; items: Ranked[] }
  /** [stuckAt]: the line about long-open tasks nobody has explained, which opens the questions about each. */
  | { t: 'review'; title: string; lines: string[]; stuckAt?: number }
  | { t: 'agenda'; title: string; summary: string; data: AgendaOut }
  | { t: 'range'; title: string; proposals: Proposal[]; accepted: number[]; toCalendar?: boolean }
  /**
   * A question to Gemini: its reply (absent while waiting) or an error, the changes it suggests with their words, the
   * task keys its numbers mean, and which changes are picked and already saved.
   */
  | { t: 'ai'; question: string; reply?: string; error?: string; changes: GeminiChange[]; labels: string[]; keys: string[]; picked: number[]; applied: number[] }

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
  const rendered = useRef(chat)
  rendered.current = chat
  // Subscribed as the page is committed, not after the next paint: a tap landing in between changed the chat while
  // nobody was listening, and the answer never showed. If it changed even so, draw once more.
  useLayoutEffect(() => {
    const l = () => force((n) => n + 1)
    listeners.add(l)
    if (rendered.current !== chat) l()
    return () => { listeners.delete(l) }
  }, [])
  return [chat, (change) => { chat = change(chat); listeners.forEach((l) => l()) }]
}

let pending: string | null = null

/** A question handed over from elsewhere (quick add's "ask the assistant"), asked once the assistant opens. */
export const pendingQuestion = {
  set: (text: string) => { pending = text },
  take: (): string | null => { const t = pending; pending = null; return t },
}
