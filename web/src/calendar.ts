import { DriveError, Drive } from './drive'
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
  start?: { date?: string; dateTime?: string }
  end?: { date?: string; dateTime?: string }
  attendees?: { self?: boolean; responseStatus?: string }[]
}

/** Events overlapping [from, to) in every calendar the owner shows, soonest first. */
export async function readEvents(drive: Drive, from: Date, to: Date): Promise<FocusIn['events']> {
  try {
    const cals: { id: string; selected?: boolean }[] = (await drive.json(`${API}/users/me/calendarList?minAccessRole=reader&maxResults=50`)).items ?? []
    const out: FocusIn['events'] = []
    for (const cal of cals.filter((c) => c.selected !== false).slice(0, 20)) {
      const params = new URLSearchParams({
        timeMin: from.toISOString(), timeMax: to.toISOString(), singleEvents: 'true', orderBy: 'startTime', maxResults: '250',
      })
      const items: GoogleEvent[] = (await drive.json(`${API}/calendars/${encodeURIComponent(cal.id)}/events?${params}`)).items ?? []
      for (const e of items) {
        if (e.status === 'cancelled' || e.attendees?.some((a) => a.self && a.responseStatus === 'declined')) continue
        const allDay = !!e.start?.date
        const begin = allDay ? `${e.start!.date}T00:00` : e.start?.dateTime ? localIso(new Date(e.start.dateTime)) : null
        const end = allDay ? `${e.end?.date ?? e.start!.date}T00:00` : e.end?.dateTime ? localIso(new Date(e.end.dateTime)) : null
        if (begin && end) out.push({ id: out.length + 1, title: e.summary ?? '(ไม่มีชื่อ)', begin, end, allDay })
      }
    }
    return out.sort((a, b) => a.begin.localeCompare(b.begin))
  } catch (e) {
    if (e instanceof DriveError && e.status === 403) {
      const off = /accessNotConfigured|has not been used|is disabled|SERVICE_DISABLED/i.test(e.message)
      throw new CalendarError(off ? 'off' : 'denied', e.message)
    }
    throw e
  }
}
