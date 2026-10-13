import { useEffect, useState } from 'preact/hooks'
import { AuthExpired } from '../drive'
import { dismiss, done, onReminders, showing, snooze, start, type Reminder } from '../reminders'
import './reminders.css'

/**
 * The task reminders that rang (reminders.ts), over every page: done, in an hour, tomorrow, or closed, the buttons of
 * Android's notification. Each stays until the owner acts on it, or its task is done elsewhere.
 */
export function Reminders() {
  const [list, setList] = useState<Reminder[]>(showing)
  const [busy, setBusy] = useState<string | null>(null)
  const [failed, setFailed] = useState<{ id: string; text: string } | null>(null)
  useEffect(() => {
    start()
    return onReminders(() => setList(showing()))
  }, [])

  const tick = async (r: Reminder) => {
    setBusy(r.id)
    setFailed(null)
    // The sign-in lasts an hour: a reminder put off past it needs the page read again (which renews it) first.
    const ok = await done(r).catch((e) => (e instanceof AuthExpired ? 'auth' : false))
    setBusy(null)
    if (ok !== true) setFailed({ id: r.id, text: ok === 'auth' ? 'หมดเวลาเข้าสู่ระบบ โหลดหน้าใหม่แล้วลองอีกครั้ง' : 'ติ๊กไม่สำเร็จ ลองอีกครั้ง' })
  }

  if (list.length === 0) return null
  return (
    <div class="reminders" aria-live="polite">
      {list.map((r) => (
        <section key={r.id} class="reminder" role="group" aria-label={`เตือน ${r.title}`}>
          <div class="reminder-text">
            <span class="reminder-head">
              <span class="reminder-time">{r.at.slice(11, 16)}</span>
              <span class="strong ellipsis">{r.title}</span>
            </span>
            {r.text && <span class="muted small">{r.text}</span>}
            {failed?.id === r.id && <span class="error small" role="alert">{failed.text}</span>}
          </div>
          <div class="reminder-actions">
            {r.raw && (
              <>
                <button class="link" disabled={busy === r.id} onClick={() => tick(r)}>เสร็จ</button>
                <button class="link" disabled={busy === r.id} onClick={() => snooze(r, 60)}>อีก 1 ชั่วโมง</button>
                <button class="link" disabled={busy === r.id} onClick={() => snooze(r, 24 * 60)}>พรุ่งนี้</button>
              </>
            )}
            <button class="link quiet" aria-label={`ปิดการเตือน ${r.title}`} onClick={() => dismiss(r)}>ปิด</button>
          </div>
        </section>
      ))}
    </div>
  )
}
