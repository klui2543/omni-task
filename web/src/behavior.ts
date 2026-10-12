// The behavior log (`omni-behavior.json` in the Omni folder; see Behavior.kt): what the owner does, on the phone, the
// watch and here, for the assistant to learn from. Events wait on this device until they are added to the file, which
// is only ever added to, one event per line. Before the file, this device kept its own log of when work got done and
// the questions answered "no"; those come into the file once, with ids every device makes alike, so they count once.
import { WebAssistantApi } from './kotlin/OmniTask-shared.mjs'
import { omniDirPath, today } from './core'
import { Drive, DriveError, type Versioned } from './drive'
import type { Task } from './types'

const api = WebAssistantApi.getInstance()

const NAME = 'omni-behavior.json'
const WAITING_KEY = 'omni.behavior.waiting'
const OLD_DONE_KEY = 'omni.doneLog'
const OLD_DECLINED_KEY = 'omni.declinedInsights'

const read = (key: string): string[] => {
  try {
    const v = JSON.parse(localStorage.getItem(key) ?? '[]')
    return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []
  } catch {
    return []
  }
}
const write = (key: string, value: string[]) => {
  try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* just not kept */ }
}
const drop = (key: string) => {
  try { localStorage.removeItem(key) } catch { /* nothing kept */ }
}

const pad = (n: number) => String(n).padStart(2, '0')
/** A moment in the device's own time zone as `yyyy-MM-ddTHH:mm:ss`. */
const stamp = (d = new Date()) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

/** The log this device kept before the file, as event lines. */
const oldLines = (): string[] => {
  const done = read(OLD_DONE_KEY)
  const lines = done.length > 0 ? api.behaviorLegacy(JSON.stringify(done)).split('\n') : []
  return [...lines, ...read(OLD_DECLINED_KEY).map((id) => api.behaviorDeclined(id, stamp()))].filter((l) => l)
}

/** The file's text as last read or written here; null until then. */
let fileText: string | null = null
let sync: BehaviorSync | null = null
let timer: ReturnType<typeof setTimeout> | undefined

/** Keeps an event line for the file and sends it a little later, with any others made meanwhile. */
const keep = (line: string) => {
  if (!line) return
  write(WAITING_KEY, [...read(WAITING_KEY), line])
  clearTimeout(timer)
  timer = setTimeout(() => { sync?.send().catch(() => {}) }, 5000)
}

/** What the owner just did, for the log. */
export const learn = {
  done: (t: Task) => keep(api.behaviorEvent('done', t.raw, stamp(), null)),
  created: (sentence: string) => keep(api.behaviorCreated(sentence, stamp())),
  /** One of [t]'s dates set to [to] (null: cleared); nothing when the day stays. */
  moved: (t: Task, field: 'DUE' | 'SCHEDULED' | 'START', to: string | null) =>
    keep(api.behaviorEvent('move', t.raw, stamp(), JSON.stringify({ field, to }))),
  declined: (ask: string) => keep(api.behaviorDeclined(ask, stamp())),
}

/** The log as the assistant reads it: the file as last read, with what waits on this device and its old log. */
export const behaviorText = (): string => [fileText ?? '', ...read(WAITING_KEY), ...oldLines()].join('\n')

/** The log file of one vault on Drive: read and topped up when the page loads, and after each event. */
export class BehaviorSync {
  private fileId: string | null = null

  constructor(private drive: Drive, private rootId: string, private ensureFolders: (path: string) => Promise<string>) {
    sync = this
  }

  private async read(): Promise<(Versioned & { id: string }) | null> {
    if (!this.fileId) this.fileId = (await this.drive.resolve(this.rootId, `${omniDirPath}/${NAME}`))?.id ?? null
    if (!this.fileId) return null
    try {
      return { ...(await this.drive.readText(this.fileId)), id: this.fileId }
    } catch (e) {
      if (e instanceof DriveError && e.status === 404) {
        this.fileId = null
        return null
      }
      throw e
    }
  }

  /** Adds what waits here to the file (made when missing), then lets go of what was sent. */
  async send(): Promise<void> {
    for (let attempt = 0; attempt < 3; attempt++) {
      const waiting = read(WAITING_KEY)
      const sending = [...waiting, ...oldLines()].join('\n')
      const file = await this.read()
      if (!file && !sending) return
      const text = api.behaviorMerge(file?.text ?? null, sending, today())
      if (file && text === file.text) {
        fileText = text
      } else if (!file) {
        this.fileId = await this.drive.createText(await this.ensureFolders(omniDirPath), NAME, text, 'application/json')
        fileText = text
      } else {
        if ((await this.drive.version(file.id)) !== file.version) continue
        await this.drive.writeText(file.id, text, 'application/json')
        fileText = text
      }
      // Lines kept while this was sent stay for the next time.
      write(WAITING_KEY, read(WAITING_KEY).filter((l) => !waiting.includes(l)))
      drop(OLD_DONE_KEY)
      drop(OLD_DECLINED_KEY)
      return
    }
  }
}
