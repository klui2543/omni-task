// When each repeating task was completed, as Android's RecurHistory: a repeating line is moved on in place rather than
// copied, so its history lives here, as "title|yyyy-MM-dd", and travels with Android's settings file (`recurHistory`,
// see settingsSync.ts). The oldest entries fall off, so the list stays small.
import { settingsBus } from './settingsBus'

const KEY = 'omni.recurHistory'
const MAX = 500

const day = (e: string) => e.slice(e.lastIndexOf('|') + 1)

/** The newest [MAX] entries, each once, oldest first. */
const keep = (entries: string[]) => [...new Set(entries)].sort((a, b) => day(a).localeCompare(day(b))).slice(-MAX)

export const recurHistory = {
  get(): string[] {
    try {
      const v = JSON.parse(localStorage.getItem(KEY) ?? '[]')
      return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []
    } catch {
      return []
    }
  },
  set(entries: string[]) {
    try { localStorage.setItem(KEY, JSON.stringify(keep(entries))) } catch { /* just not kept */ }
  },
  /** A repeating task was completed on [date]. */
  add(title: string, date: string) {
    recurHistory.set([...recurHistory.get(), `${title}|${date}`])
    settingsBus.changed()
  },
}
