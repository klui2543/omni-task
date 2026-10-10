import { useEffect, useRef, useState } from 'preact/hooks'
import { Auth, CALENDAR_SCOPE } from './auth'
import { CalendarError } from './calendar'
import type { PageProps } from './Home'
import type { FocusIn } from './types'
import { parseDay } from './ui/views/dates'

export const CALENDAR_OFF =
  'เปิด Google Calendar API ในโปรเจกต์ Google Cloud ของแอปนี้ก่อน (APIs & Services > Library > Google Calendar API > Enable) แล้วกดโหลดใหม่'

/**
 * Google Calendar events from [from] up to [to] (YYYY-MM-DD, local days), read once the owner has agreed.
 * Read again when the range moves or the note is reloaded, but not more than once a minute for the same range,
 * as the Focus page does. [note] says why nothing could be read.
 */
export function useCalendarFeed(p: PageProps, from: string, to: string) {
  const connected = Auth.granted(CALENDAR_SCOPE)
  const [events, setEvents] = useState<FocusIn['events']>([])
  const [note, setNote] = useState('')
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
        setNote('')
      },
      (e) => {
        if (last.current.key !== key) return
        last.current = { key: '', at: 0 }
        setNote(e instanceof CalendarError ? (e.code === 'off' ? CALENDAR_OFF : 'Google ยังไม่อนุญาตให้อ่านปฏิทิน กดอนุญาตอีกครั้ง') : 'อ่านปฏิทินไม่ได้ ลองโหลดใหม่')
      },
    )
  }, [connected, from, to, p.snapshot])

  return { connected, events: connected ? events : [], note }
}
