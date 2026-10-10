import { DriveError, Drive } from './drive'
import { hiddenCalendars } from './calendarChoice'
import type { FocusIn } from './types'

// Google Calendar, read-only: the owner's visible calendars around today, as the events Android reads from the
// phone's calendar (Google Calendar syncs into it). Only used on the Focus page.

const API = 'https://www.googleapis.com/calendar/v3'

/** [off]: the Calendar API is not switched on for this Google project. [denied]: the owner has not agreed (yet). */
export class CalendarError extends Error {
  constructor(public code: 'off' | 'denied', message: string) {
    super(message)
  }
}

const pad = (n: number) => String(n).padStart(2, '0')
/** A moment in the device's own time zone as `yyyy-MM-ddTHH:mm`, the form the shared logic reads. */
export const localIso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

interface GoogleEvent {
  id: string
  status?: string
  summary?: string
  htmlLink?: string
  start?: { date?: string; dateTime?: string }
  end?: { date?: string; dateTime?: string }
  attendees?: { self?: boolean; responseStatus?: string }[]
}

// The shared logic tells events apart by a number, so each Google event keeps the same one for as long as the page lives.
const ids = new Map<string, number>()
const idOf = (key: string) => {
  let id = ids.get(key)
  if (id === undefined) ids.set(key, (id = ids.size + 1))
  return id
}

/** The calendars the owner shows, remembered for ten minutes (they rarely change). */
const calendarLists = new WeakMap<Drive, { at: number; cals: CalendarInfo[] }>()

/** One of the owner's Google calendars, as the picker in Settings lists it. */
export interface CalendarInfo {
  id: string
  name: string
  primary: boolean
}

/** The calendars Google shows the owner (the ones switched on in Google Calendar), by name. */
export async function listCalendars(drive: Drive): Promise<CalendarInfo[]> {
  const hit = calendarLists.get(drive)
  if (hit && Date.now() - hit.at < 10 * 60_000) return hit.cals
  try {
    const all: { id: string; selected?: boolean; summary?: string; summaryOverride?: string; primary?: boolean }[] =
      (await drive.json(`${API}/users/me/calendarList?minAccessRole=reader&maxResults=50`)).items ?? []
    const cals = all.filter((c) => c.selected !== false).map((c) => ({ id: c.id, name: c.summaryOverride ?? c.summary ?? c.id, primary: !!c.primary }))
    calendarLists.set(drive, { at: Date.now(), cals })
    return cals
  } catch (e) {
    throw asCalendarError(e)
  }
}

/** The calendars to read: those shown in Google Calendar, less the ones the owner switched off here. */
async function calendarsOf(drive: Drive) {
  const off = new Set(hiddenCalendars.get())
  return (await listCalendars(drive)).filter((c) => !off.has(c.id)).slice(0, 20)
}

/** Events overlapping [from, to) in every calendar the owner shows, soonest first. */
export async function readEvents(drive: Drive, from: Date, to: Date): Promise<FocusIn['events']> {
  try {
    const cals = await calendarsOf(drive)
    const out: FocusIn['events'] = []
    const params = new URLSearchParams({
      timeMin: from.toISOString(), timeMax: to.toISOString(), singleEvents: 'true', orderBy: 'startTime', maxResults: '2500',
    })
    // Every calendar is asked at the same time, not one after the other; a long range may come in more than one page.
    const pages = async (cal: CalendarInfo) => {
      const items: GoogleEvent[] = []
      let token: string | undefined
      do {
        const page = await drive.json(`${API}/calendars/${encodeURIComponent(cal.id)}/events?${params}${token ? `&pageToken=${encodeURIComponent(token)}` : ''}`)
        items.push(...((page.items ?? []) as GoogleEvent[]))
        token = page.nextPageToken
      } while (token && items.length < 10_000)
      return items
    }
    const lists = await Promise.all(cals.map(async (cal) => ({ cal, items: await pages(cal) })))
    for (const { cal, items } of lists) {
      for (const e of items) {
        if (e.status === 'cancelled' || e.attendees?.some((a) => a.self && a.responseStatus === 'declined')) continue
        const allDay = !!e.start?.date
        const begin = allDay ? `${e.start!.date}T00:00` : e.start?.dateTime ? localIso(new Date(e.start.dateTime)) : null
        const end = allDay ? `${e.end?.date ?? e.start!.date}T00:00` : e.end?.dateTime ? localIso(new Date(e.end.dateTime)) : null
        if (begin && end) out.push({ id: idOf(`${cal.id}|${e.id}`), title: e.summary ?? '(ไม่มีชื่อ)', begin, end, allDay, ...(e.htmlLink ? { link: e.htmlLink } : {}) })
      }
    }
    return out.sort((a, b) => a.begin.localeCompare(b.begin))
  } catch (e) {
    throw asCalendarError(e)
  }
}

/** Google's 403 as a CalendarError (switched off for the project, or not agreed to); anything else as it is. */
function asCalendarError(e: unknown) {
  if (e instanceof DriveError && e.status === 403) {
    const off = /accessNotConfigured|has not been used|is disabled|SERVICE_DISABLED/i.test(e.message)
    return new CalendarError(off ? 'off' : 'denied', e.message)
  }
  return e
}

/** An event to put on the owner's own calendar: all day on [day] when [start] is null, else [minutes] long from [start] (HH:mm). */
export interface NewEvent {
  title: string
  day: string
  start: string | null
  minutes: number
}

const addDay = (iso: string) => {
  const d = new Date(iso + 'T00:00')
  d.setDate(d.getDate() + 1)
  return localIso(d).slice(0, 10)
}

/** Adds an event to the primary calendar (needs the calendar.events permission) and returns its page in Google Calendar. */
export async function createEvent(drive: Drive, e: NewEvent): Promise<string> {
  try {
    let body: unknown
    if (e.start === null) {
      body = { summary: e.title, start: { date: e.day }, end: { date: addDay(e.day) } }
    } else {
      const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
      const begin = new Date(`${e.day}T${e.start}`)
      const end = new Date(begin.getTime() + e.minutes * 60_000)
      const at = (d: Date) => ({ dateTime: `${localIso(d)}:00`, timeZone })
      body = { summary: e.title, start: at(begin), end: at(end) }
    }
    const made = await drive.postJson(`${API}/calendars/primary/events`, body)
    return made.htmlLink ?? ''
  } catch (err) {
    throw asCalendarError(err)
  }
}
