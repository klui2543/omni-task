import { readEvents } from './calendar'
import type { Drive } from './drive'
import type { FocusIn } from './types'

type Events = FocusIn['events']

/** How long events read once are used again before Google is asked (a reload, or an event added here, asks sooner). */
const KEEP_MS = 2 * 60_000

/**
 * Calendar events as last read for each range, so a page opened again shows them at once and asks Google only
 * when they are older than [KEEP_MS]. The same range asked twice at once is read once.
 */
export function calendarCache(drive: Drive) {
  const kept = new Map<string, { events: Events; at: number }>()
  const reading = new Map<string, Promise<Events>>()
  const keyOf = (from: Date, to: Date) => `${from.getTime()}|${to.getTime()}`

  return {
    read(from: Date, to: Date): Promise<Events> {
      const key = keyOf(from, to)
      const hit = kept.get(key)
      if (hit && Date.now() - hit.at < KEEP_MS) return Promise.resolve(hit.events)
      let pending = reading.get(key)
      if (!pending) {
        pending = readEvents(drive, from, to)
          .then((events) => {
            kept.set(key, { events, at: Date.now() })
            if (kept.size > 8) kept.delete(kept.keys().next().value!)
            return events
          })
          .finally(() => reading.delete(key))
        reading.set(key, pending)
      }
      return pending
    },
    /** The events last read for the range, however old, or undefined when it was not read yet. */
    peek(from: Date, to: Date): Events | undefined {
      return kept.get(keyOf(from, to))?.events
    },
    /** Everything is read again on next use. */
    stale() {
      kept.forEach((v, k) => kept.set(k, { ...v, at: 0 }))
    },
  }
}
