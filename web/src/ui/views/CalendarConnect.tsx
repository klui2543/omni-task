import type { CalendarFeed } from '../../calendarFeed'
import { ConnectCard } from '../GoogleConnect'

/**
 * The card that asks to read Google Calendar, and the reason when it could not be read (or no calendar was found)
 * with "ลองอีกครั้ง" to read it again, as Android's "ตรวจอีกครั้ง"; as on the Focus page.
 */
export function CalendarConnect(p: Pick<CalendarFeed, 'connected' | 'note' | 'denied' | 'retry'> & { onConnect: () => void }) {
  return (
    <>
      {!p.connected && <ConnectCard note="ให้เว็บอ่านนัดและเวรจาก Google Calendar เพื่อจัดเวลาว่าง และให้ผู้ช่วยลงนัดให้ได้" onConnect={p.onConnect} />}
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
