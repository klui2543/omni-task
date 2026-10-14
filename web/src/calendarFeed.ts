import { useEffect, useRef, useState } from 'preact/hooks'
import { Auth, CALENDAR_SCOPE } from './auth'
import { CalendarError } from './calendar'
import { hiddenCalendars } from './calendarChoice'
import type { PageProps } from './Home'
import type { FocusIn } from './types'
import { parseDay } from './ui/views/dates'

export const CALENDAR_OFF =
  'เปิด Google Calendar API ในโปรเจกต์ Google Cloud ของแอปนี้ก่อน (APIs & Services > Library > Google Calendar API > Enable) แล้วกดโหลดใหม่'

const NO_CALENDAR = 'ไม่พบปฏิทินในบัญชี Google นี้ เปิดปฏิทินใน Google Calendar แล้วกดลองอีกครั้ง'
const ALL_HIDDEN = 'ปิดทุกปฏิทินไว้ในตั้งค่า (ปฏิทินที่แสดง) เปิดอย่างน้อยหนึ่งปฏิทินแล้วกดลองอีกครั้ง'

export type CalendarFeed = ReturnType<typeof useCalendarFeed>

/**
 * Google Calendar events from [from] up to [to] (YYYY-MM-DD, local days), read once the owner has agreed.
 * Read again when the range moves or the note is reloaded, but not more than once a minute for the same range,
 * as the Focus page does. [note] says why nothing could be read (no calendar found counts, as on Android), [denied]
 * that Google refused, and [retry] reads again at once.
 */
export function useCalendarFeed(p: PageProps, from: string, to: string) {
  const connected = Auth.granted(CALENDAR_SCOPE)
  const [events, setEvents] = useState<FocusIn['events']>(() => p.peekCalendar(parseDay(from), parseDay(to)) ?? [])
  const [note, setNote] = useState('')
  const [denied, setDenied] = useState(false)
  const [tries, setTries] = useState(0)
  const last = useRef({ key: '', at: 0 })

  useEffect(() => {
    if (!connected || !p.snapshot) return
    const key = `${from}|${to}`
    if (last.current.key === key && Date.now() - last.current.at < 60_000) return
    last.current = { key, at: Date.now() }
    p.readCalendar(parseDay(from), parseDay(to)).then(
      (e) => {
        if (last.current.key !== key) return
        setEvents(e)
        setDenied(false)
        if (e.length > 0) { setNote(''); return }
        // Nothing read: say so when there is no calendar to read from (the list is kept, so this asks Google rarely).
        p.listCalendars().then(
          (cals) => {
            if (last.current.key !== key) return
            const off = new Set(hiddenCalendars.get())
            setNote(cals.length === 0 ? NO_CALENDAR : cals.every((c) => off.has(c.id)) ? ALL_HIDDEN : '')
          },
          () => setNote(''),
        )
      },
      (e) => {
        if (last.current.key !== key) return
        last.current = { key: '', at: 0 }
        setDenied(e instanceof CalendarError && e.code === 'denied')
        setNote(e instanceof CalendarError ? (e.code === 'off' ? CALENDAR_OFF : 'Google ยังไม่อนุญาตให้อ่านปฏิทิน กดอนุญาตอีกครั้ง') : 'อ่านปฏิทินไม่ได้ กดลองอีกครั้ง')
      },
    )
  }, [connected, from, to, p.snapshot, tries])

  // "ลองอีกครั้ง": what was read is let go (a reload marks it stale) and the range is read again now.
  const retry = () => {
    last.current = { key: '', at: 0 }
    p.onReload()
    setTries((n) => n + 1)
  }

  return { connected, events: connected ? events : [], note, denied, retry }
}
