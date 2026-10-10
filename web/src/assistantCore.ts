// The Assistant's rule-based logic, compiled from the shared Kotlin (Profile, Insight, Planner, Focus.rank, DayPlan...).
// Each call takes the TaskForge note's text and a small state; the answers come back as JSON.
import { WebAssistantApi } from './kotlin/OmniTask-shared.mjs'
import { taskFilePath, today } from './core'
import type { EditResult } from './types'
import type { Snapshot } from './vault'
import { doneLog } from './assistantState'
import './assistantVault'

const api = WebAssistantApi.getInstance()

export interface EventIn {
  id: number
  title: string
  begin: string
  end: string
  allDay: boolean
}

/** What the page knows besides the note, as WebAssistant.StateIn takes it. */
export interface AssistState {
  now: string
  profile: string | null
  events: EventIn[]
  doneLog: string[]
  declined: string[]
  insightId?: string
  futureCount?: number
  skippedToday?: string[]
}

export interface ProfileOut {
  exists: boolean
  wake: string
  sleep: string
  focusFrom: string
  focusTo: string
  exercise: string
  shiftWords: string[]
  nightWords: string[]
  bestDay: string | null
  remembered: string[]
  updated: string | null
  stale: boolean
}

/** One change to the profile note; whatever is left out stays as the note has it. */
export interface ProfileChange {
  wake?: string
  sleep?: string
  focusFrom?: string
  focusTo?: string
  exercise?: string
  bestDay?: string
  remember?: string[]
  described?: Record<string, string>
}

export interface Ask {
  id: string
  text: string
  remember: string
  window: string
}

export type Route = { kind: 'plan' | 'agenda'; from: string; to: string } | { kind: 'rank' | 'slots' | 'duration' }

export interface SlotOut {
  day: string
  dayLabel: string
  start: string
  end: string
  title: string
  why: string
  score: number
}

export interface PlanOut {
  request: string
  title: string
  kind: string
  kindLabel: string
  minutes: number
  intro: string
  slots: SlotOut[]
}

export interface Ranked {
  key: string
  title: string
  reason: string
}

export interface AgendaOut {
  events: number
  due: number
  busiest: string | null
  days: { day: string; events: { title: string; allDay: boolean; time?: string }[]; tasks: { key: string; title: string; due: boolean }[] }[]
}

export interface Proposal {
  key: string
  title: string
  raw: string
  lineIndex: number
  day: string
  start: string
  minutes: number
}

const text = (s: Snapshot) => s.noteText()
const key = (s: Snapshot) => s.noteKey()
const run = <T>(json: string): T => JSON.parse(json)

export const profileOf = (profile: string | null): ProfileOut => run(api.profile(profile, today()))

/** The profile note's text after [change], read from the note as it stands (for Vault.changeProfile). */
export const profileApply = (current: string | null, change: ProfileChange): string => api.profileApply(current, JSON.stringify(change), today())

export const insight = (s: Snapshot, st: AssistState): Ask | null => run(api.insight(key(s), taskFilePath, text(s), JSON.stringify(st)))

/** The profile note after saying yes to the question named in [st].insightId; null when it is no longer asked. */
export const insightYes = (s: Snapshot, st: AssistState, current: string | null): string | null => {
  const out = api.insightYes(key(s), taskFilePath, text(s), JSON.stringify({ ...st, profile: current }))
  return out === '' ? null : out
}

export const kindOf = (title: string): string => api.kindOf(title)

export const route = (request: string): Route => run(api.route(request, today()))

export const slots = (s: Snapshot, st: AssistState, request: string, minutes: number): PlanOut =>
  run(api.slots(key(s), taskFilePath, text(s), JSON.stringify(st), request, minutes))

export const rank = (s: Snapshot, st: AssistState): Ranked[] => run(api.rank(key(s), taskFilePath, text(s), JSON.stringify(st)))

export const todayList = (s: Snapshot, st: AssistState): Ranked[] => run(api.today(key(s), taskFilePath, text(s), JSON.stringify(st)))

export const agenda = (s: Snapshot, st: AssistState, from: string, to: string): AgendaOut =>
  run(api.agenda(key(s), taskFilePath, text(s), JSON.stringify(st), from, to))

export const planRange = (s: Snapshot, st: AssistState, from: string, to: string): Proposal[] =>
  run(api.planRange(key(s), taskFilePath, text(s), JSON.stringify(st), from, to))

export const weekly = (s: Snapshot, st: AssistState): { title: string; lines: string[] } =>
  run(api.weekly(key(s), taskFilePath, text(s), JSON.stringify(st)))

/** The picture of the day Android hands to the Claude app. */
export const snapshotText = (s: Snapshot, st: AssistState): string => api.snapshot(key(s), taskFilePath, text(s), JSON.stringify(st))

export const slotLine = (title: string, day: string, start: string | null, todayIso = today()): string => api.slotLine(title, day, start, todayIso)

export const addLines = (noteText: string, lines: string[]) => run<EditResult>(api.addLines(noteText, JSON.stringify(lines)))

export const scheduleTasks = (noteText: string, items: { raw: string; lineIndex: number; day: string; time: string }[]) =>
  run<{ ok: boolean; text: string; done: boolean[] }>(api.scheduleTasks(noteText, JSON.stringify(items)))

export const sweep = (live: string, archive: string, days: number): { text: string; archive: string; titles: string[] } | null =>
  run(api.sweep(live, archive, days, today()))

/** Remembers that a task of this title was just done, so the assistant can notice when the owner does which kind of work. */
export const logDone = (title: string) => doneLog.add(kindOf(title))
