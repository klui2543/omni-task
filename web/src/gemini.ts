import { WebAssistantApi } from './kotlin/OmniTask-shared.mjs'
import { today } from './core'
import type { AssistState } from './assistantCore'
import type { EditResult } from './types'
import type { Snapshot } from './vault'

// The assistant on Gemini, with the owner's own API key (Google's free tier). The question and the reading of the
// answer come from the shared Kotlin (Gemini.kt), as on Android; the call itself goes straight from the browser to
// Google. The key stays in this browser only: settingsSync.ts never copies it to the vault.

const api = WebAssistantApi.getInstance()

const KEY = 'omni.gemini'

export interface GeminiSetup {
  key: string
  model: string
  /** Typed questions go to Gemini (true) or to the rule-based assistant. */
  chat: boolean
}

/** One change Gemini suggests, as Gemini.Change: [index] is the task's number less one (-1 for "add"). */
export interface GeminiChange {
  op: 'done' | 'due' | 'scheduled' | 'start' | 'remind' | 'priority' | 'add'
  index: number
  date?: string | null
  time?: string | null
  priority?: string | null
  title?: string | null
  why: string
}

export interface GeminiAnswer {
  reply: string
  changes: GeminiChange[]
  /** Each change in words, for the card. */
  labels: string[]
  /** The task keys in the order of the numbers in the question. */
  keys: string[]
}

export const geminiSetup = {
  get(): GeminiSetup {
    try {
      const kept = JSON.parse(localStorage.getItem(KEY) ?? '{}')
      return { key: kept.key ?? '', model: kept.model ?? '', chat: kept.chat ?? true }
    } catch {
      return { key: '', model: '', chat: true }
    }
  },
  set(change: Partial<GeminiSetup>) {
    const next = { ...geminiSetup.get(), ...change }
    try { localStorage.setItem(KEY, JSON.stringify(next)) } catch { /* just not kept */ }
    return next
  },
}

export class GeminiError extends Error {}

/** Asks Gemini; [history] is the earlier questions and replies of this conversation. Throws GeminiError with words for the owner. */
export async function askGemini(s: Snapshot, st: AssistState, history: { question: string; reply: string }[], question: string): Promise<GeminiAnswer> {
  const setup = geminiSetup.get()
  if (!setup.key) throw new GeminiError('ใส่ API key ของ Gemini ก่อน ที่หน้าตั้งค่า')
  const ask: { body: string; keys: string[] } = JSON.parse(
    api.geminiRequest(s.noteKey(), s.path, s.noteText(), JSON.stringify(st), JSON.stringify(history), question),
  )
  let res: Response
  try {
    res = await fetch(api.geminiEndpoint(setup.model), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': setup.key },
      body: ask.body,
    })
  } catch {
    throw new GeminiError('ต่อ Gemini ไม่ได้ ตรวจอินเทอร์เน็ตแล้วลองใหม่')
  }
  const text = await res.text()
  if (!res.ok) throw new GeminiError(api.geminiError(res.status, text))
  const out: Omit<GeminiAnswer, 'keys'> & { error?: string | null } = JSON.parse(api.geminiAnswer(text, ask.keys.length))
  if (out.error) throw new GeminiError(out.error)
  return { reply: out.reply, changes: out.changes, labels: out.labels, keys: ask.keys }
}

/** The note with the date, priority and reminder changes for one task, in one write. */
export const geminiLine = (noteText: string, raw: string, lineIndex: number, changes: GeminiChange[]): EditResult =>
  JSON.parse(api.geminiLine(noteText, raw, lineIndex, JSON.stringify(changes)))

/** The task lines for the "add" changes. */
export const geminiNewLines = (changes: GeminiChange[]): string[] => JSON.parse(api.geminiNewLines(JSON.stringify(changes), today()))

/** The picked changes by task, done winning over the rest of its task's changes (Gemini.byTask). */
export function byTask(changes: GeminiChange[]): Map<number, GeminiChange[]> {
  const out = new Map<number, GeminiChange[]>()
  for (const c of changes) {
    if (c.op === 'add') continue
    out.set(c.index, [...(out.get(c.index) ?? []), c])
  }
  for (const [i, list] of out) {
    const done = list.find((c) => c.op === 'done')
    if (done) out.set(i, [done])
  }
  return out
}
