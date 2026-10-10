import { localIso, readEvents } from './calendar'
import { hiddenCalendars } from './calendarChoice'
import type { Drive } from './drive'
import type { FocusIn } from './types'

type Events = FocusIn['events']

/** How long events read once are used again before Google is asked (a reload, or an event added here, asks sooner). */
const KEEP_MS = 2 * 60_000

/** The weeks around today read in one go: every page's first range (Focus, Gantt, the month, the assistant) falls inside. */
const BEFORE_DAYS = 42
const AFTER_DAYS = 84

interface Kept { from: number; to: number; hidden: string; events: Events; at: number }

/** Today's window: from six weeks back to twelve weeks on, at local midnight. */
function homeWindow(): { from: Date; to: Date } {
  const from = new Date(); from.setHours(0, 0, 0, 0); from.setDate(from.getDate() - BEFORE_DAYS)
  const to = new Date(); to.setHours(0, 0, 0, 0); to.setDate(to.getDate() + AFTER_DAYS)
  return { from, to }
}

/** The events of a wider read that overlap [from, to). */
function within(events: Events, from: Date, to: Date): Events {
  const a = localIso(from), b = localIso(to)
  return events.filter((e) => e.begin < b && (e.end > a || e.begin >= a))
}

/**
 * Calendar events as last read, so a page opened again shows them at once and asks Google only when they are
 * older than [KEEP_MS]. A range near today is read as the whole window around today, so the Focus page, the
 * Gantt, the month and the assistant all answer from the one read; a range further out is read on its own.
 * The same read asked twice at once is made once.
 */
export function calendarCache(drive: Drive) {
  let kept: Kept[] = []
  const reading = new Map<string, Promise<Events>>()
  // Bumped when everything goes stale: a read that began before (an event added meanwhile) is not kept as fresh.
  let round = 0

  /** The read that holds all of [from, to) for the calendars shown now, fresh only when [fresh] is set. */
  const cover = (from: Date, to: Date, fresh: boolean) => {
    const hidden = hiddenCalendars.get().join(',')
    return kept.find((k) => k.hidden === hidden && k.from <= from.getTime() && k.to >= to.getTime() && (!fresh || Date.now() - k.at < KEEP_MS))
  }

  const fetch = (from: Date, to: Date): Promise<Events> => {
    // The calendars switched off are part of the key, so a new choice is read at once.
    const hidden = hiddenCalendars.get().join(',')
    const key = `${from.getTime()}|${to.getTime()}|${hidden}`
    let pending = reading.get(key)
    if (!pending) {
      const began = round
      pending = readEvents(drive, from, to)
        .then((events) => {
          if (began !== round) return events
          kept = [{ from: from.getTime(), to: to.getTime(), hidden, events, at: Date.now() },
            ...kept.filter((k) => !(k.hidden === hidden && k.from >= from.getTime() && k.to <= to.getTime()))].slice(0, 8)
          return events
        })
        .finally(() => { if (reading.get(key) === pending) reading.delete(key) })
      reading.set(key, pending)
    }
    return pending
  }

  return {
    read(from: Date, to: Date): Promise<Events> {
      const hit = cover(from, to, true)
      if (hit) return Promise.resolve(within(hit.events, from, to))
      const home = homeWindow()
      const near = from >= home.from && to <= home.to
      return (near ? fetch(home.from, home.to) : fetch(from, to)).then((events) => (near ? within(events, from, to) : events))
    },
    /** The events last read for the range, however old, or undefined when it was not read yet. */
    peek(from: Date, to: Date): Events | undefined {
      const hit = cover(from, to, false)
      return hit && within(hit.events, from, to)
    },
    /** Reads today's window ahead, so the first page that shows the calendar has it at once. */
    warm() {
      const home = homeWindow()
      if (!cover(home.from, home.to, true)) fetch(home.from, home.to).catch(() => {})
    },
    /** Everything is read again on next use. */
    stale() {
      round++
      reading.clear()
      kept = kept.map((k) => ({ ...k, at: 0 }))
    },
  }
}
