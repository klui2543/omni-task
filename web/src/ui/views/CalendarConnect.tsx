import type { CalendarFeed } from '../../calendarFeed'
import { host } from '../../host'

/**
 * The card that asks to read Google Calendar, and the reason when it could not be read (or no calendar was found)
 * with "ลองอีกครั้ง" to read it again, as Android's "ตรวจอีกครั้ง"; as on the Focus page.
 */
export function CalendarConnect(p: Pick<CalendarFeed, 'connected' | 'note' | 'denied' | 'retry'> & { onConnect: () => void }) {
  return (
    <>
      {!p.connected && host.google && (
        <section class="fcard connect">
          <div class="grow">
            <div class="strong">เชื่อมต่อ Google Calendar</div>
            <div class="muted small">ให้เว็บอ่านนัดและเวรจาก Google Calendar เพื่อจัดเวลาว่าง และให้ผู้ช่วยลงนัดให้ได้</div>
          </div>
          <button class="primary" onClick={p.onConnect}>อนุญาต</button>
        </section>
      )}
      {p.connected && p.note && (
        <section class="notice" role="alert">
          <span class="grow">{p.note}</span>
          {p.denied && <button class="link" onClick={p.onConnect}>อนุญาต</button>}
          <button class="link" onClick={p.retry}>ลองอีกครั้ง</button>
        </section>
      )}
    </>
  )
}
